package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.resources.SpringResource;
import org.apache.ignite.services.Service;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.services.WorkloadOrchestrationService;
import org.kinotic.system.api.services.WorkloadService;

import java.time.Duration;
import java.util.Date;

/**
 * Deletes the records of runs that ended longer ago than {@code kinotic.systemApi.workload.retentionDays},
 * with their logs, through {@link WorkloadOrchestrationService#deleteWorkload(String)}; runs as one HA
 * cluster singleton on the Ignite service grid, once when it starts and hourly after.
 */
@Slf4j
public class WorkloadRetentionSweeper implements Service {

    static final String SINGLETON_NAME = "workload-retention";

    private static final long SWEEP_MS = 3_600_000;
    private static final int PAGE_SIZE = 100;

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = WorkloadOrchestrationService.class)
    private transient WorkloadOrchestrationService orchestrationService;
    @SpringResource(resourceClass = WorkloadService.class)
    private transient WorkloadService workloadService;
    @SpringResource(resourceClass = KinoticSystemApiProperties.class)
    private transient KinoticSystemApiProperties properties;
    @SpringResource(resourceClass = Vertx.class)
    private transient Vertx vertx;

    private transient long timerId;

    @Override
    public void init() {
        log.info("Starting workload retention sweep singleton");
        sweep();
        timerId = vertx.setPeriodic(SWEEP_MS, id -> sweep());
    }

    @Override
    public void execute() {
        // passive service: all work is driven by the timer started in init
    }

    @Override
    public void cancel() {
        log.info("Stopping workload retention sweep singleton");
        vertx.cancelTimer(timerId);
    }

    // One page per sweep; what a page leaves, the next sweep takes. A record that cannot be deleted
    // is logged and left for the next sweep, so one failure does not hold the rest.
    private void sweep() {
        int retentionDays = properties.getSystemApi().getWorkload().getRetentionDays();
        Date cutoff = new Date(System.currentTimeMillis() - Duration.ofDays(retentionDays).toMillis());
        workloadService.findEndedBefore(cutoff, Pageable.create(0, PAGE_SIZE, null))
                       .compose(page -> {
                           Future<Void> chain = Future.succeededFuture();
                           for (Workload workload : page.getContent()) {
                               chain = chain.compose(v -> orchestrationService.deleteWorkload(workload.getId())
                                                                              .onSuccess(deleted -> log.info("Deleted workload {} ({}): its run ended {}, past the {} day retention",
                                                                                                             workload.getId(), workload.getName(), workload.getUpdated(), retentionDays))
                                                                              .recover(error -> {
                                                                                  log.warn("Workload {} could not be deleted by the retention sweep", workload.getId(), error);
                                                                                  return Future.succeededFuture();
                                                                              }));
                           }
                           return chain;
                       })
                       .onFailure(error -> log.error("Workload retention sweep failed", error));
    }
}
