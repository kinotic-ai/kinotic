package org.kinotic.system.internal.api.services.workload;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.resources.SpringResource;
import org.apache.ignite.services.Service;

/**
 * Runs the {@link WorkloadRetentionSweeper} as one HA cluster singleton on the Ignite service grid,
 * once when it starts and hourly after.
 */
@Slf4j
public class WorkloadCleanupService implements Service {

    static final String SINGLETON_NAME = "workload-cleanup";

    private static final long SWEEP_MS = 3_600_000;

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = WorkloadRetentionSweeper.class)
    private transient WorkloadRetentionSweeper sweeper;
    @SpringResource(resourceClass = Vertx.class)
    private transient Vertx vertx;

    private transient long timerId;

    @Override
    public void init() {
        log.info("Starting workload cleanup singleton");
        sweep();
        timerId = vertx.setPeriodic(SWEEP_MS, id -> sweep());
    }

    @Override
    public void execute() {
        // passive service: all work is driven by the timer started in init
    }

    @Override
    public void cancel() {
        log.info("Stopping workload cleanup singleton");
        vertx.cancelTimer(timerId);
    }

    private void sweep() {
        int retentionDays = sweeper.getRetentionDays();
        sweeper.sweep(deleted -> log.info("Deleted {} workloads whose runs ended more than {} days ago", deleted, retentionDays))
               .onFailure(error -> log.error("Workload cleanup failed; what it left is taken by the next run", error));
    }
}
