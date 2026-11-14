package org.kinotic.grind;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.ServerInfo;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.grind.internal.api.services.DefaultJobScheduler;
import org.kinotic.grind.internal.api.services.DefaultJobService;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Scheduled jobs: a job runs when it is first scheduled and every period after, one run a period
 * however many nodes schedule it, each run recorded as any other, and a run due while another run
 * of the job executes starts once that run has ended.
 */
public class ScheduleTest extends AbstractGrindTest {

    private static final String JOB_NAME = "scheduled-job";

    private static final Duration SHORT = Duration.ofMillis(100);
    private static final Duration LONG = Duration.ofHours(1);

    private DefaultJobScheduler scheduler;
    // a second scheduler and job service on the same Vert.x instance and ledger, as another node would be
    private DefaultJobScheduler otherScheduler;

    private final AtomicInteger runs = new AtomicInteger();

    @BeforeEach
    void setUpSchedulers() {
        scheduler = new DefaultJobScheduler(jobService, vertx);
        DefaultJobService otherJobService = new DefaultJobService(repository, new ObjectMapper(), vertx,
                                                                  () -> new ServerInfo("other-node", "grind test node"));
        otherJobService.setApplicationContext(appCtx);
        otherScheduler = new DefaultJobScheduler(otherJobService, vertx);
    }

    @Test
    public void aScheduledJobRunsWhenScheduledAndEveryPeriodAfter() throws Exception {
        scheduler.schedule(this::countingJob, JobOwner.system(), SHORT);

        awaitUntil("three scheduled runs", () -> runs.get() >= 3);
        awaitUntil("the runs to be recorded as completed",
                   () -> repository.savedRuns.values().stream()
                                             .filter(run -> JOB_NAME.equals(run.getName()) && run.getStatus() == ExecutionStatus.COMPLETED)
                                             .count() >= 3);
    }

    @Test
    public void aJobTwoNodesScheduleRunsOnceAPeriod() throws Exception {
        scheduler.schedule(this::countingJob, JobOwner.system(), LONG);
        otherScheduler.schedule(this::countingJob, JobOwner.system(), LONG);

        awaitUntil("the first scheduled run", () -> runs.get() >= 1);
        Thread.sleep(300);
        assertEquals(1, runs.get());
    }

    @Test
    public void aJobIsScheduledForEachOwnerOnItsOwn() throws Exception {
        scheduler.schedule(this::countingJob, JobOwner.ofOrganization("acme", null), LONG);
        scheduler.schedule(this::countingJob, JobOwner.ofOrganization("globex", null), LONG);

        awaitUntil("a run for each owner", () -> runs.get() >= 2);
    }

    @Test
    public void aRunDueWhileAnotherExecutesStartsOnceItHasEnded() throws Exception {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        JobRunHandle executing = jobService.run(JobDefinition.create("held")
                                                             .name(JOB_NAME).version("1")
                                                             .exclusive()
                                                             .task(Tasks.fromCallable("hold", () -> gate)),
                                                JobOwner.system());
        executing.getEvents().subscribe(event -> { }, error -> { });
        awaitUntil("the held run's task to start", () -> {
            var record = repository.taskAt(executing.getJobRunId(), "0/1");
            return record != null && record.getStatus() == ExecutionStatus.RUNNING;
        });

        scheduler.schedule(this::countingJob, JobOwner.system(), LONG);
        Thread.sleep(300);
        assertEquals(0, runs.get());

        gate.complete(null);
        assertNull(await(executing).error());
        // the job stays due, so the next check starts the run although its period is an hour
        otherScheduler.schedule(this::countingJob, JobOwner.system(), LONG);
        awaitUntil("the scheduled run", () -> runs.get() == 1);
    }

    @Test
    public void aJobThatIsNotExclusiveIsNotScheduled() {
        assertThrows(IllegalArgumentException.class,
                     () -> scheduler.schedule(() -> JobDefinition.create("shared")
                                                                 .name("shared-job").version("1")
                                                                 .task(Tasks.fromRunnable("quick", () -> { })),
                                              JobOwner.system(), SHORT));
    }

    private JobDefinition countingJob() {
        return JobDefinition.create("scheduled")
                            .name(JOB_NAME).version("1")
                            .exclusive()
                            .task(Tasks.fromRunnable("count", runs::incrementAndGet));
    }

}
