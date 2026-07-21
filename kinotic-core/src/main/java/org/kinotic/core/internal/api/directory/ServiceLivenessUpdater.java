package org.kinotic.core.internal.api.directory;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.resources.SpringResource;
import org.apache.ignite.services.Service;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.ServiceListenerChange;
import org.kinotic.core.api.event.ServiceListenerContinuityLost;
import org.kinotic.core.api.directory.ServiceDirectory;
import reactor.core.Disposable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The single cluster-wide maintainer of the directory's {@code online} liveness flag, running as one HA cluster
 * singleton on the Ignite service grid. All three liveness layers are uniform — signal &rarr; verify &rarr; write:
 * <ul>
 *   <li>on start, on a periodic timer, and on a {@link org.kinotic.core.api.event.ServiceListenerContinuityLost},
 *       reconcile against the full snapshot of active service addresses;</li>
 *   <li>on each {@link org.kinotic.core.api.event.ServiceListenerChange}, re-verify the address's current
 *       registrations (coalesced per address: at most one verify per window) and write the verified state — a
 *       change is an invalidation trigger, never a value.</li>
 * </ul>
 */
@Slf4j
public class ServiceLivenessUpdater implements Service {

    private static final long VERIFY_WINDOW_MS = 2_000;
    private static final long RECONCILE_INTERVAL_MS = 600_000;

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = EventBusService.class)
    private transient EventBusService eventBusService;
    @SpringResource(resourceClass = ServiceDirectory.class)
    private transient ServiceDirectory serviceDirectory;
    @SpringResource(resourceClass = Vertx.class)
    private transient Vertx vertx;

    private transient Disposable subscription;
    private transient long reconcileTimerId;
    // address -> the pending verify timer, so a burst of changes for one address collapses to a single verify
    private transient ConcurrentMap<String, Long> pendingVerifications;

    @Override
    public void init() {
        log.info("Starting service liveness updater singleton");
        // this instance was serialized to the hosting node, so runtime state is created here rather
        // than in field initializers, which do not run on deserialization
        pendingVerifications = new ConcurrentHashMap<>();
        // subscribe before snapshotting so a change between the two cannot be missed; verify is
        // idempotent, so changes arriving while the reconcile runs are harmless
        subscribeToEvents();
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
        if (subscription != null) {
            subscription.dispose();
        }
        vertx.cancelTimer(reconcileTimerId);
        pendingVerifications.values().forEach(vertx::cancelTimer);
        pendingVerifications.clear();
    }

    private void subscribeToEvents() {
        subscription = eventBusService.monitorServiceListenerEvents()
                                      .subscribe(event -> {
                                          switch (event) {
                                              case ServiceListenerChange change -> onChange(change.address());
                                              // changes may have been missed while continuity was lost, and a
                                              // service that went offline during the gap will never emit another
                                              // change — so waiting for per-address changes cannot catch up;
                                              // reconcile() corrects every entry against a fresh snapshot
                                              case ServiceListenerContinuityLost ignored -> reconcile();
                                          }
                                      },
                                      // the hot event stream never terminates; an error here is a bug, and the
                                      // periodic reconcile bounds the resulting staleness
                                      error -> log.error("Service listener event stream failed", error));
    }

    private void onChange(String address) {
        // one verify per window: verify(addr) reads the CURRENT registration state at fire time, so
        // every change arriving while the timer is pending is covered by the single fire
        pendingVerifications.computeIfAbsent(address, addr -> vertx.setTimer(VERIFY_WINDOW_MS, id -> {
            pendingVerifications.remove(addr);
            verify(addr);
        }));
    }

    private void verify(String address) {
        serviceDirectory.verifyLiveness(address)
                        .exceptionally(throwable -> {
                            log.error("Failed to write verified liveness for {}", address, throwable);
                            return null;
                        });
    }

    private void reconcile() {
        serviceDirectory.reconcileLiveness()
                        .exceptionally(throwable -> {
                            log.error("Liveness reconciliation failed", throwable);
                            return null;
                        });
    }

}
