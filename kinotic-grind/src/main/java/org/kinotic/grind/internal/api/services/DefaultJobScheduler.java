package org.kinotic.grind.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Counter;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.grind.api.model.ExclusiveJobRunningException;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.services.JobScheduler;
import org.kinotic.grind.api.services.JobService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Default {@link JobScheduler}: every node that schedules a job checks on a timer of its own whether
 * the job is due, and the cluster keeps when the job's last scheduled run started, so the node whose
 * check first claims a due run starts it through its own {@link JobService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultJobScheduler implements JobScheduler {

    // How often a node checks a job whose period is longer than this, which is also how long after
    // a refused run's cause has gone the run starts
    private static final long CHECK_MS = 60_000;

    private final JobService jobService;
    private final Vertx vertx;

    private final List<Long> timers = new CopyOnWriteArrayList<>();

    @Override
    public void schedule(Supplier<JobDefinition> definitions, JobOwner owner, Duration period) {
        Validate.notNull(definitions, "definitions cannot be null");
        Validate.notNull(owner, "owner cannot be null");
        Validate.notNull(period, "period cannot be null");
        Validate.isTrue(period.toMillis() > 0, "period must be at least a millisecond");
        JobDefinition described = definitions.get();
        Validate.notBlank(described.getName(), "JobDefinition name must be set to schedule");
        // the claim of a due run and its start are two steps, so only the job's own lock keeps a run
        // that outlasts its period from executing beside the next
        Validate.isTrue(described.isExclusive(), "The scheduled job %s must be exclusive", described.getName());

        String jobName = described.getName();
        String lastStartedName = "grind.schedule:" + DefaultJobService.ownedJobName(jobName, owner);
        long periodMs = period.toMillis();
        log.info("Scheduling the job {} every {}", jobName, period);
        Runnable check = () -> startWhenDue(definitions, owner, jobName, lastStartedName, periodMs);
        check.run();
        timers.add(vertx.setPeriodic(Math.min(periodMs, CHECK_MS), id -> check.run()));
    }

    @PreDestroy
    void cancelTimers() {
        timers.forEach(vertx::cancelTimer);
    }

    private void startWhenDue(Supplier<JobDefinition> definitions, JobOwner owner, String jobName,
                              String lastStartedName, long periodMs) {
        vertx.sharedData()
             .getCounter(lastStartedName)
             .compose(lastStarted -> lastStarted.get().compose(last -> {
                 long now = System.currentTimeMillis();
                 // one node's claim of the due run succeeds; the others find the time moved on
                 Future<Boolean> claim = now - last < periodMs ? Future.succeededFuture(false)
                                                               : lastStarted.compareAndSet(last, now);
                 return claim.onSuccess(claimed -> {
                     if (claimed) {
                         run(definitions, owner, jobName, lastStarted, last, now);
                     }
                 });
             }))
             .onFailure(error -> log.error("Could not check whether the scheduled job {} is due", jobName, error));
    }

    // Starts the claimed run; its outcome is recorded on the run, and logged here
    private void run(Supplier<JobDefinition> definitions, JobOwner owner, String jobName,
                     Counter lastStarted, long last, long claimed) {
        // Future.future fails the run when building or starting it throws, so that is logged as any failed run is
        Future.<Void>future(promise -> jobService.run(definitions.get(), owner).completion().onComplete(promise))
              .onFailure(error -> {
                  if (error instanceof ExclusiveJobRunningException) {
                      // the cause tells a run that is going from a lock the cluster could not grant
                      log.debug("The scheduled run of the job {} waits for the run going: {}, caused by {}",
                                jobName, error.getMessage(), String.valueOf(error.getCause()));
                      // the refused run never executed, so the claim is handed back and the job stays due
                      lastStarted.compareAndSet(claimed, last)
                                 .onFailure(e -> log.error("Could not leave the scheduled job {} due; its next run is a period away", jobName, e));
                  } else {
                      log.error("The scheduled run of the job {} failed; its next run is a period away", jobName, error);
                  }
              });
    }

}
