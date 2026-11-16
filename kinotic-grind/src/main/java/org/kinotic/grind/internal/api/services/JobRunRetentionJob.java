package org.kinotic.grind.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.utils.CursorPaging;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.services.SystemJob;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Deletes the job runs that ended more than {@link #RETENTION} ago, each with its task records, as a
 * system job the cluster's job manager runs daily. A run still executing is kept however long ago it
 * started. The latest run of each job, by the job's name and the organization it ran for, is kept
 * however long ago it ended, since that is the run a deployment shows as its latest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunRetentionJob implements SystemJob {

    // As long as the change ledger keeps the entries of a run's changes (kinotic_watch_event's DATA_RETENTION)
    static final Duration RETENTION = Duration.ofDays(30);

    private static final int PAGE_SIZE = 100;

    private final JobRunRepository jobRuns;

    @Override
    public JobDefinition createJobDefinition() {
        return JobDefinition.create("Deletes the job runs that ended more than " + RETENTION.toDays()
                                            + " days ago, with their task records, keeping the latest run of each job")
                            .name("job-run-retention-sweep")
                            .version("1")
                            .every(Duration.ofDays(1))
                            .task(Tasks.fromCallable("Delete expired job runs", this::sweep));
    }

    private Future<Void> sweep() {
        Date cutoff = new Date(System.currentTimeMillis() - RETENTION.toMillis());
        // paged by cursor, since the runs that are kept stay in the listing
        return CursorPaging.forEachPage(pageable -> jobRuns.findEndedBefore(cutoff, pageable), PAGE_SIZE, Sort.by("id"),
                                        this::deleteExpired);
    }

    private Future<Void> deleteExpired(List<JobRun> ended) {
        List<String> expired = new ArrayList<>();
        Future<Void> judged = Future.succeededFuture();
        for (JobRun run : ended) {
            judged = judged.compose(v -> jobRuns.findLatestRun(run.getName(), run.getOrganizationId()))
                           .map(latest -> {
                               if (latest != null && !latest.getId().equals(run.getId())) {
                                   expired.add(run.getId());
                               }
                               return null;
                           });
        }
        return judged.compose(v -> expired.isEmpty()
                ? Future.succeededFuture()
                : jobRuns.deleteRuns(expired)
                         .onSuccess(deleted -> log.info("Deleted {} job runs that ended more than {} days ago", expired.size(), RETENTION.toDays())));
    }

}
