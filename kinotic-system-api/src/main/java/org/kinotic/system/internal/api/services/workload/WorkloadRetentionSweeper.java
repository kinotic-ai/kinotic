package org.kinotic.system.internal.api.services.workload;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.repositories.WorkloadRepository;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.services.workload.WorkloadOrchestrationService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.List;

/**
 * Deletes the records of workload runs that ended longer ago than
 * {@code kinotic.systemApi.workload.retentionDays}, with their logs, a batch at a time through
 * {@link WorkloadOrchestrationService#deleteWorkloads(List)}. One sweep deletes at most
 * {@value #MAX_BATCHES} batches of {@value #BATCH_SIZE}, leaving the rest to the next.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkloadRetentionSweeper {

    static final int BATCH_SIZE = 100;
    static final int MAX_BATCHES = 50;

    private final WorkloadOrchestrationService orchestrationService;
    private final WorkloadRepository workloadRepository;
    private final KinoticSystemApiProperties properties;

    /**
     * @return how many days a run's record is kept after the run ended
     */
    public int getRetentionDays() {
        return properties.getSystemApi().getWorkload().getRetentionDays();
    }

    /**
     * Runs one sweep. A batch that fails ends the sweep with that failure: a batch's logs go before its
     * records, so nothing is half deleted, and what is left is taken by the next sweep.
     *
     * @return a future completing once the sweep has ended
     */
    public Future<Void> sweep() {
        int retentionDays = getRetentionDays();
        Date cutoff = new Date(System.currentTimeMillis() - Duration.ofDays(retentionDays).toMillis());
        return sweepBatch(cutoff, retentionDays, 1);
    }

    // The oldest page is deleted as one batch, and the next page read once it is gone, until a page
    // comes back short or the sweep has taken its share
    private Future<Void> sweepBatch(Date cutoff, int retentionDays, int batchNumber) {
        return workloadRepository.findEndedBefore(cutoff, Pageable.create(0, BATCH_SIZE, null))
                                 .compose(page -> {
                                     List<String> ids = page.getContent().stream().map(Workload::getId).toList();
                                     Future<Void> ret;
                                     if (ids.isEmpty()) {
                                         ret = Future.succeededFuture();
                                     } else {
                                         ret = orchestrationService.deleteWorkloads(ids)
                                                 .onSuccess(v -> log.info("Deleted {} workloads whose runs ended more than {} days ago", ids.size(), retentionDays))
                                                 .compose(v -> ids.size() == BATCH_SIZE && batchNumber < MAX_BATCHES
                                                         ? sweepBatch(cutoff, retentionDays, batchNumber + 1)
                                                         : Future.succeededFuture());
                                     }
                                     return ret;
                                 });
    }
}
