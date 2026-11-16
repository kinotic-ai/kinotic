package org.kinotic.grind.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.services.SystemJob;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.List;

/**
 * Deletes the job runs that ended more than {@link #RETENTION} ago, each with its task records, as a
 * system job the cluster's job manager runs daily. A run still executing is kept however long ago it
 * started. One run of the job deletes at most {@value #MAX_BATCHES} batches of {@value #BATCH_SIZE},
 * leaving the rest to the next.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunRetentionJob implements SystemJob {

    // As long as the change ledger keeps the entries of a run's changes (kinotic_watch_event's DATA_RETENTION)
    static final Duration RETENTION = Duration.ofDays(30);
    static final int BATCH_SIZE = 100;
    static final int MAX_BATCHES = 50;

    private final JobRunRepository jobRuns;

    @Override
    public JobDefinition createJobDefinition() {
        return JobDefinition.create("Deletes the job runs that ended more than " + RETENTION.toDays()
                                            + " days ago, with their task records")
                            .name("job-run-retention-sweep")
                            .version("1")
                            .every(Duration.ofDays(1))
                            .task(Tasks.fromCallable("Delete expired job runs",
                                                     () -> sweepBatch(new Date(System.currentTimeMillis() - RETENTION.toMillis()), 1)));
    }

    // The first page is deleted as one batch, and the page read again once it is gone, until a page
    // comes back short or the run has taken its share
    private Future<Void> sweepBatch(Date cutoff, int batchNumber) {
        return jobRuns.findEndedBefore(cutoff, Pageable.create(0, BATCH_SIZE, null))
                      .compose(page -> {
                          List<String> ids = page.getContent().stream().map(JobRun::getId).toList();
                          Future<Void> ret;
                          if (ids.isEmpty()) {
                              ret = Future.succeededFuture();
                          } else {
                              ret = jobRuns.deleteRuns(ids)
                                           .onSuccess(v -> log.info("Deleted {} job runs that ended more than {} days ago", ids.size(), RETENTION.toDays()))
                                           .compose(v -> ids.size() == BATCH_SIZE && batchNumber < MAX_BATCHES
                                                   ? sweepBatch(cutoff, batchNumber + 1)
                                                   : Future.succeededFuture());
                          }
                          return ret;
                      });
    }

}
