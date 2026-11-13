package org.kinotic.grind;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.ServerInfo;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.grind.internal.api.services.DefaultJobService;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exclusive jobs: one run of a job's name executes at a time for an owner, whichever job
 * service starts it, and a run or resume started meanwhile is refused with nothing recorded.
 */
public class ExclusiveTest extends AbstractGrindTest {

    private static final String JOB_NAME = "exclusive-job";

    // a second job service on the same Vert.x instance and ledger, as another node would be
    private DefaultJobService otherJobService;

    @BeforeEach
    void setUpOtherJobService() {
        otherJobService = new DefaultJobService(repository, new ObjectMapper(), vertx,
                                                () -> new ServerInfo("other-node", "grind test node"));
        otherJobService.setApplicationContext(appCtx);
    }

    @Test
    public void aRunIsRefusedWhileAnotherExecutes() throws Exception {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        JobRunHandle executing = startAndAwaitExecuting(gatedJob(gate), JobOwner.system());

        JobRunHandle refusedHandle = otherJobService.run(gatedJob(gate), JobOwner.system());
        RunResult refused = await(refusedHandle);

        assertInstanceOf(IllegalStateException.class, refused.error());
        assertNull(repository.savedRuns.get(refusedHandle.getJobRunId()));

        gate.complete(null);
        assertNull(await(executing).error());
    }

    @Test
    public void theJobRunsAgainOnceTheRunHasEnded() throws Exception {
        CompletableFuture<Void> gate = CompletableFuture.completedFuture(null);

        assertNull(await(jobService.run(gatedJob(gate), JobOwner.system())).error());
        assertNull(await(otherJobService.run(gatedJob(gate), JobOwner.system())).error());
    }

    @Test
    public void theJobRunsAgainOnceTheRunIsCancelled() throws Exception {
        JobRunHandle executing = startAndAwaitExecuting(gatedJob(new CompletableFuture<>()), JobOwner.system());

        executing.cancel();
        awaitUntil("the run to record CANCELLED",
                   () -> repository.savedRuns.get(executing.getJobRunId()).getStatus() == ExecutionStatus.CANCELLED);

        RunResult next = await(otherJobService.run(gatedJob(CompletableFuture.completedFuture(null)),
                                                   JobOwner.system()));
        assertNull(next.error());
    }

    @Test
    public void theJobExecutesForTwoOwnersAtOnce() throws Exception {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        JobRunHandle executing = startAndAwaitExecuting(gatedJob(gate), JobOwner.ofOrganization("acme", "site"));

        CompletableFuture<Void> done = CompletableFuture.completedFuture(null);
        assertNull(await(otherJobService.run(gatedJob(done), JobOwner.ofOrganization("globex", "site"))).error());
        assertNull(await(otherJobService.run(gatedJob(done), JobOwner.ofOrganization("acme", "docs"))).error());

        gate.complete(null);
        assertNull(await(executing).error());
    }

    @Test
    public void ownersWhoseIdsRunTogetherTheSameWayStayApart() throws Exception {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        JobRunHandle executing = startAndAwaitExecuting(gatedJob(gate), JobOwner.ofApplication("acme", "a:b", "c"));

        CompletableFuture<Void> done = CompletableFuture.completedFuture(null);
        assertNull(await(otherJobService.run(gatedJob(done), JobOwner.ofApplication("acme", "a", "b:c"))).error());
        assertNull(await(otherJobService.run(gatedJob(done), JobOwner.ofOrganization("acme", ""))).error());
        assertNull(await(otherJobService.run(gatedJob(done), JobOwner.ofOrganization("acme", null))).error());

        gate.complete(null);
        assertNull(await(executing).error());
    }

    @Test
    public void aResumeIsRefusedWhileAnotherRunExecutes() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        JobRunHandle failed = jobService.run(failingJob(fail), JobOwner.system());
        assertNotNull(await(failed).error());

        CompletableFuture<Void> gate = new CompletableFuture<>();
        JobRunHandle executing = startAndAwaitExecuting(gatedJob(gate), JobOwner.system());

        fail.set(false);
        JobRunHandle refusedHandle = otherJobService.resume(failed.getJobRunId(), failingJob(fail));
        RunResult refused = await(refusedHandle);

        assertInstanceOf(IllegalStateException.class, refused.error());
        assertNull(repository.savedRuns.get(refusedHandle.getJobRunId()));

        gate.complete(null);
        assertNull(await(executing).error());
        assertNull(await(otherJobService.resume(failed.getJobRunId(), failingJob(fail))).error());
    }

    @Test
    public void aJobThatIsNotExclusiveExecutesTwiceAtOnce() throws Exception {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        JobDefinition job = JobDefinition.create("shared")
                                         .name("shared-job").version("1")
                                         .task(Tasks.fromCallable("hold", () -> gate));
        assertFalse(job.isExclusive());
        JobRunHandle executing = startAndAwaitExecuting(job, JobOwner.system());

        JobDefinition second = JobDefinition.create("shared")
                                            .name("shared-job").version("1")
                                            .task(Tasks.fromRunnable("quick", () -> { }));
        assertNull(await(otherJobService.run(second, JobOwner.system())).error());

        gate.complete(null);
        assertNull(await(executing).error());
    }

    private JobRunHandle startAndAwaitExecuting(JobDefinition job, JobOwner owner) throws InterruptedException {
        JobRunHandle ret = jobService.run(job, owner);
        ret.getEvents().subscribe(event -> { }, error -> { });
        awaitUntil("the run's task to start", () -> {
            var record = repository.taskAt(ret.getJobRunId(), "0/1");
            return record != null && record.getStatus() == ExecutionStatus.RUNNING;
        });
        return ret;
    }

    private static JobDefinition gatedJob(CompletableFuture<Void> gate) {
        return JobDefinition.create("exclusive")
                            .name(JOB_NAME).version("1")
                            .exclusive()
                            .task(Tasks.fromCallable("hold", () -> gate));
    }

    private static JobDefinition failingJob(AtomicBoolean fail) {
        return JobDefinition.create("exclusive")
                            .name(JOB_NAME).version("1")
                            .exclusive()
                            .task(Tasks.fromRunnable("flaky", () -> {
                                if (fail.get()) {
                                    throw new IllegalStateException("boom");
                                }
                            }));
    }

}
