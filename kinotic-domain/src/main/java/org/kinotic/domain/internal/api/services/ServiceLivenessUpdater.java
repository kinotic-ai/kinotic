package org.kinotic.domain.internal.api.services;

import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.domain.internal.api.repositories.ServiceDirectoryEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.Disposable;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The single cluster-wide maintainer of the directory's {@code online} liveness flag.
 * <p>
 * This runs as one HA cluster singleton (deployed via {@code IgniteServiceAdapter}), so it is instantiated by Ignite
 * and Spring-wired via field injection rather than a constructor. All three liveness layers are uniform —
 * signal &rarr; verify &rarr; write:
 * <ul>
 *   <li>on start and on a periodic timer, reconcile against the full snapshot of active service addresses;</li>
 *   <li>on each {@link org.kinotic.core.api.event.ListenerChange}, re-verify the address's current registrations
 *       (debounced per address) and write the verified state — a change is an invalidation trigger, never a value.</li>
 * </ul>
 */
@Slf4j
public class ServiceLivenessUpdater {

    private static final long DEBOUNCE_MS = 2_000;
    private static final long RECONCILE_INTERVAL_MS = 600_000;

    @Autowired
    private EventBusService eventBusService;
    @Autowired
    private ServiceDirectoryEntryRepository repository;
    @Autowired
    private Vertx vertx;

    private Disposable subscription;
    private long reconcileTimerId = -1;
    // address -> the pending verify timer, so a burst of changes for one address collapses to a single verify
    private final ConcurrentMap<String, Long> pendingVerifications = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        log.info("Starting service liveness updater singleton");
        reconcile();
        subscription = eventBusService.monitorListenerChanges()
                                      .subscribe(change -> onChange(change.address()),
                                                 error -> log.error("Listener change stream failed", error));
        reconcileTimerId = vertx.setPeriodic(RECONCILE_INTERVAL_MS, id -> reconcile());
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping service liveness updater singleton");
        if (subscription != null) {
            subscription.dispose();
        }
        if (reconcileTimerId != -1) {
            vertx.cancelTimer(reconcileTimerId);
        }
        pendingVerifications.values().forEach(vertx::cancelTimer);
        pendingVerifications.clear();
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
