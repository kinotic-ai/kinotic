package org.kinotic.domain.internal.api.services;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.resources.SpringResource;
import org.apache.ignite.services.Service;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.domain.internal.api.repositories.ServiceDirectoryEntryRepository;
import reactor.core.Disposable;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The single cluster-wide maintainer of the directory's {@code online} liveness flag, running as one HA cluster
 * singleton on the Ignite service grid. All three liveness layers are uniform — signal &rarr; verify &rarr; write:
 * <ul>
 *   <li>on start and on a periodic timer, reconcile against the full snapshot of active service addresses;</li>
 *   <li>on each {@link org.kinotic.core.api.event.ListenerChange}, re-verify the address's current registrations
 *       (debounced per address) and write the verified state — a change is an invalidation trigger, never a value.</li>
 * </ul>
 */
@Slf4j
public class ServiceLivenessUpdater implements Service {

    private static final long DEBOUNCE_MS = 2_000;
    private static final long RECONCILE_INTERVAL_MS = 600_000;
    private static final long RESUBSCRIBE_DELAY_MS = 5_000;

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = EventBusService.class)
    private transient EventBusService eventBusService;
    @SpringResource(resourceClass = ServiceDirectoryEntryRepository.class)
    private transient ServiceDirectoryEntryRepository repository;
    @SpringResource(resourceClass = Vertx.class)
    private transient Vertx vertx;

    private transient volatile boolean cancelled;
    private transient Disposable subscription;
    private transient long reconcileTimerId;
    // address -> the pending verify timer, so a burst of changes for one address collapses to a single verify
    private transient ConcurrentMap<String, Long> pendingVerifications;

    @Override
    public void init() {
        log.info("Starting service liveness updater singleton");
        // this instance was serialized to the hosting node, so runtime state is created here rather
        // than in field initializers, which do not run on deserialization
        cancelled = false;
        pendingVerifications = new ConcurrentHashMap<>();
        // subscribe before snapshotting so a change between the two cannot be missed; verify is
        // idempotent, so changes arriving while the reconcile runs are harmless
        subscribeToChanges();
        reconcile();
        reconcileTimerId = vertx.setPeriodic(RECONCILE_INTERVAL_MS, id -> reconcile());
    }

    @Override
    public void execute() {
        // passive service: all work is driven by the change stream and timers started in init
    }

    @Override
    public void cancel() {
        log.info("Stopping service liveness updater singleton");
        cancelled = true;
        if (subscription != null) {
            subscription.dispose();
        }
        vertx.cancelTimer(reconcileTimerId);
        pendingVerifications.values().forEach(vertx::cancelTimer);
        pendingVerifications.clear();
    }

    private void subscribeToChanges() {
        subscription = eventBusService.monitorListenerChanges()
                                      .subscribe(change -> onChange(change.address()),
                                                 error -> {
                                                     // the stream errors when registration update continuity is
                                                     // lost; recover by re-snapshotting and resubscribing
                                                     log.warn("Listener change stream failed; reconciling and resubscribing", error);
                                                     vertx.setTimer(RESUBSCRIBE_DELAY_MS, id -> {
                                                         if (!cancelled) {
                                                             subscribeToChanges();
                                                             reconcile();
                                                         }
                                                     });
                                                 });
    }

    private void onChange(String address) {
        pendingVerifications.compute(address, (addr, existingTimer) -> {
            if (existingTimer != null) {
                vertx.cancelTimer(existingTimer);
            }
            return vertx.setTimer(DEBOUNCE_MS, id -> {
                pendingVerifications.remove(addr);
                verify(addr);
            });
        });
    }

    private void verify(String address) {
        eventBusService.isAnybodyListening(CRI.create(address)).onComplete(result -> {
            if (result.succeeded()) {
                repository.setOnlineByAddress(address, result.result(), Instant.now())
                          .exceptionally(throwable -> {
                              log.error("Failed to write verified liveness for {}", address, throwable);
                              return null;
                          });
            } else {
                log.error("Failed to verify listeners for {}", address, result.cause());
            }
        });
    }

    private void reconcile() {
        eventBusService.activeServiceAddresses().onComplete(result -> {
            if (result.succeeded()) {
                repository.reconcileLiveness(result.result(), Instant.now())
                          .exceptionally(throwable -> {
                              log.error("Liveness reconciliation failed", throwable);
                              return null;
                          });
            } else {
                log.error("Liveness reconciliation failed to snapshot active addresses", result.cause());
            }
        });
    }

}
