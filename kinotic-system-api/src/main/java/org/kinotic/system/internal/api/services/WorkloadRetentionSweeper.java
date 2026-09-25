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
import java.util.List;

/**
 * Deletes the records of runs that ended longer ago than {@code kinotic.systemApi.workload.retentionDays},
 * with their logs, a page at a time through {@link WorkloadOrchestrationService#deleteWorkloads(List)};
 * runs as one HA cluster singleton on the Ignite service grid, once when it starts and hourly after.
 */
@Slf4j
public class WorkloadRetentionSweeper implements Service {

    static final String SINGLETON_NAME = "workload-retention";

    private static final long SWEEP_MS = 3_600_000;
    private static final int PAGE_SIZE = 100;
    // Pages one sweep takes before leaving the rest to the next: a bound on one sweep's run, not on
    // what is eventually deleted
    private static final int MAX_PAGES = 50;

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

    private void sweep() {
        int retentionDays = properties.getSystemApi().getWorkload().getRetentionDays();
        Date cutoff = new Date(System.currentTimeMillis() - Duration.ofDays(retentionDays).toMillis());
        sweepPage(cutoff, retentionDays, 1)
                .onFailure(error -> log.error("Workload retention sweep failed; what it left is taken by the next sweep", error));
    }

    // The oldest page is deleted as one batch, and the next page read once it is gone, until a page
    // comes back short or the sweep has taken its share. A batch that fails ends this sweep: the logs
    // go before the records, so nothing is half done.
    private Future<Void> sweepPage(Date cutoff, int retentionDays, int pageNumber) {
        return workloadService.findEndedBefore(cutoff, Pageable.create(0, PAGE_SIZE, null))
                              .compose(page -> {
                                  List<String> ids = page.getContent().stream().map(Workload::getId).toList();
                                  Future<Void> ret;
                                  if (ids.isEmpty()) {
                                      ret = Future.succeededFuture();
                                  } else {
                                      ret = orchestrationService.deleteWorkloads(ids)
                                              .onSuccess(deleted -> log.info("Deleted {} workloads whose runs ended more than {} days ago", ids.size(), retentionDays))
                                              .compose(v -> ids.size() == PAGE_SIZE && pageNumber < MAX_PAGES
                                                      ? sweepPage(cutoff, retentionDays, pageNumber + 1)
                                                      : Future.succeededFuture());
                                  }
                                  return ret;
                              });
    }
}
