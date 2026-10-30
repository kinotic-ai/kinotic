package org.kinotic.system.internal.api.services.workload;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.VertxInternal;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.resources.SpringResource;
import org.kinotic.system.internal.api.services.PeriodicSystemApiSingleton;

import java.util.concurrent.ExecutionException;

/**
 * Runs the {@link WorkloadRetentionSweeper} as one HA cluster singleton on the Ignite service grid,
 * once when it starts, hourly after, and whenever {@link #sweep()} is called through its proxy. It
 * runs one sweep at a time: a sweep requested while one is running joins it.
 */
@Slf4j
public class WorkloadCleanupService extends PeriodicSystemApiSingleton implements WorkloadRetentionService {

    private static final String SINGLETON_NAME = "workload-cleanup";

    private static final long SWEEP_MS = 3_600_000;

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = WorkloadRetentionSweeper.class)
    private transient WorkloadRetentionSweeper sweeper;

    // The context sweeps run on: one of the singleton's own, since a caller of sweep() blocks its thread
    // until the sweep ends, and a sweep queued on that caller's context would wait behind it forever
    private transient Context sweepContext;
    // The sweep in progress, null between sweeps; guarded by this
    private transient Future<Void> inFlight;

    /**
     * Deploys the singleton.
     *
     * @param ignite the local node
     */
    static void deploy(Ignite ignite) {
        systemApiServices(ignite).deployClusterSingleton(SINGLETON_NAME, new WorkloadCleanupService());
    }

    /**
     * @param ignite the local node
     * @return a proxy calling the singleton wherever it is hosted
     */
    static WorkloadRetentionService proxy(Ignite ignite) {
        // Ignite proxies a service only through an interface the service implements, here WorkloadRetentionService
        return systemApiServices(ignite).serviceProxy(SINGLETON_NAME, WorkloadRetentionService.class, false);
    }

    @Override
    public void init() {
        sweepContext = ((VertxInternal) vertx).createEventLoopContext();
        super.init();
    }

    @Override
    protected long periodMs() {
        return SWEEP_MS;
    }

    @Override
    protected void start() {
        startSweep();
    }

    @Override
    public void sweep() {
        // the calling thread blocks until the sweep it started or joined has ended, or until it is
        // interrupted, which leaves the sweep running
        try {
            startSweep().toCompletionStage().toCompletableFuture().get();
        } catch (ExecutionException e) {
            // the exception travels back to the calling node, so it carries the cause as text, which always serializes
            throw new IllegalStateException("Workload cleanup failed: " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the workload cleanup to end");
        }
    }

    private synchronized Future<Void> startSweep() {
        if (inFlight == null) {
            Promise<Void> sweep = Promise.promise();
            // assigned before the sweep starts, so its completion always finds it to clear
            inFlight = sweep.future();
            inFlight.onComplete(result -> sweepEnded(sweep.future()));
            // Future.future fails the sweep when sweeper.sweep() throws instead of returning a failed future,
            // so inFlight is always cleared
            sweepContext.runOnContext(v -> Future.<Void>future(promise -> sweeper.sweep().onComplete(promise))
                                                 .onFailure(error -> log.error("Workload cleanup failed; what it left is taken by the next run", error))
                                                 .onComplete(sweep));
        }
        return inFlight;
    }

    private synchronized void sweepEnded(Future<Void> sweep) {
        if (inFlight == sweep) {
            inFlight = null;
        }
    }
}
