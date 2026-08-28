package org.kinotic.grindv2;

import org.junit.jupiter.api.Test;
import org.kinotic.grindv2.api.model.ExecutionStatus;
import org.kinotic.grindv2.api.model.JobDefinition;
import org.kinotic.grindv2.api.model.JobOwner;
import org.kinotic.grindv2.api.model.JobRunHandle;
import org.kinotic.grindv2.api.model.events.TaskCompletedEvent;
import org.kinotic.grindv2.api.model.StoreType;
import org.kinotic.grindv2.api.model.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resume semantics per {@link StoreType}: NONE skips, RESULT re-runs or reloads, STATE
 * replays the recorded value, dynamic tasks regenerate, and the original run's identity
 * guards the resume.
 */
public class ResumeTest extends AbstractGrindV2Test {

    @Test
    public void completedNoneTaskIsSkippedOnResume() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean(true);

        JobRunHandle original = jobService.run(noneJob(calls, fail), JobOwner.system());
        assertNotNull(await(original).error());
        assertEquals(1, calls.get());

        fail.set(false);
        RunResult resumed = await(jobService.resume(original.getJobRunId(), noneJob(calls, fail)));

        assertNull(resumed.error());
        assertEquals(1, calls.get());
    }

    @Test
    public void completedResultTaskRerunsOnResume() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean(true);

        JobRunHandle original = jobService.run(resultJob(calls, fail), JobOwner.system());
        assertNotNull(await(original).error());
        assertEquals(1, calls.get());

        fail.set(false);
        RunResult resumed = await(jobService.resume(original.getJobRunId(), resultJob(calls, fail)));

        assertNull(resumed.error());
        assertEquals(2, calls.get());
    }

    @Test
    public void completedResultTaskUsesDeclaredReloadTask() throws Exception {
        AtomicInteger creates = new AtomicInteger();
        AtomicInteger reloads = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean(true);

        JobRunHandle original = jobService.run(reloadJob(creates, reloads, fail), JobOwner.system());
        assertNotNull(await(original).error());
        assertEquals(1, creates.get());
        assertEquals(0, reloads.get());

        fail.set(false);
        RunResult resumed = await(jobService.resume(original.getJobRunId(), reloadJob(creates, reloads, fail)));

        assertNull(resumed.error());
        assertEquals(1, creates.get());
        assertEquals(1, reloads.get());
    }

    @Test
    public void completedStateTaskReplaysWithoutExecuting() throws Exception {
        AtomicInteger creates = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean(true);
        AtomicReference<WidgetState> seenByLaterTask = new AtomicReference<>();

        JobRunHandle original = jobService.run(stateJob(creates, fail, seenByLaterTask), JobOwner.system());
        assertNotNull(await(original).error());
        assertEquals(1, creates.get());

        fail.set(false);
        RunResult resumed = await(jobService.resume(original.getJobRunId(), stateJob(creates, fail, seenByLaterTask)));

        assertNull(resumed.error());
        assertEquals(1, creates.get());
        assertEquals(new WidgetState("decided"), seenByLaterTask.get());

        TaskCompletedEvent replayed = resumed.events().stream()
                                        .filter(TaskCompletedEvent.class::isInstance)
                                        .map(TaskCompletedEvent.class::cast)
                                        .filter(event -> event.taskPath().equals("0/1"))
                                        .findFirst().orElseThrow();
        assertEquals(StoreType.STATE, replayed.storeType());
        assertEquals(new WidgetState("decided"), replayed.storedValue());
    }

    @Test
    public void genericStateValueFailsTheRun() throws Exception {
        JobDefinition job = JobDefinition.create("bad state")
                .name("bad-state").version("1")
                .taskStoreState(Tasks.fromValue("produce list", new ArrayList<>(List.of("a"))), "values");

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNotNull(result.error());
        assertInstanceOf(IllegalStateException.class, result.error());
        assertTrue(result.error().getMessage().contains("generic"));
        assertEquals(ExecutionStatus.FAILED, repository.savedRuns.get(handle.getJobRunId()).getStatus());
        assertEquals(ExecutionStatus.FAILED, repository.taskAt(handle.getJobRunId(), "0/1").getStatus());
    }

    @Test
    public void dynamicTasksRegenerateAndTheirCompletedChildrenSkip() throws Exception {
        AtomicInteger builds = new AtomicInteger();
        AtomicInteger innerA = new AtomicInteger();
        AtomicInteger innerB = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean(true);

        JobRunHandle original = jobService.run(dynamicJob(builds, innerA, innerB, fail), JobOwner.system());
        assertNotNull(await(original).error());
        assertEquals(1, builds.get());
        assertEquals(1, innerA.get());
        assertEquals(1, innerB.get());

        fail.set(false);
        RunResult resumed = await(jobService.resume(original.getJobRunId(),
                                                   dynamicJob(builds, innerA, innerB, fail)));

        assertNull(resumed.error());
        assertEquals(2, builds.get());
        assertEquals(1, innerA.get());
        assertEquals(1, innerB.get());
    }

    @Test
    public void resumedRunKeepsTheOriginalOwnerAndReference() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean(true);

        JobRunHandle original = jobService.run(noneJob(calls, fail), JobOwner.ofApplication("org1", "app1"));
        await(original);

        fail.set(false);
        JobRunHandle resumed = jobService.resume(original.getJobRunId(), noneJob(calls, fail));
        await(resumed);

        var run = repository.savedRuns.get(resumed.getJobRunId());
        assertEquals(original.getJobRunId(), run.getResumedFrom());
        assertEquals("org1", run.getOrganizationId());
        assertEquals("app1", run.getApplicationId());
        assertEquals(ExecutionStatus.COMPLETED, run.getStatus());
    }

    @Test
    public void resumeGuardsTheOriginalRunsIdentity() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean(true);
        JobRunHandle original = jobService.run(noneJob(calls, fail), JobOwner.system());
        await(original);

        JobDefinition wrongName = JobDefinition.create("renamed").name("some-other-job").version("1")
                .task(Tasks.fromRunnable("noop", () -> { }));
        RunResult nameMismatch = await(jobService.resume(original.getJobRunId(), wrongName));
        assertTrue(nameMismatch.error().getMessage().contains("does not match the name"));

        JobDefinition wrongVersion = noneJob(calls, fail);
        wrongVersion.version("999");
        RunResult versionMismatch = await(jobService.resume(original.getJobRunId(), wrongVersion));
        assertTrue(versionMismatch.error().getMessage().contains("does not match the version"));

        RunResult unknownRun = await(jobService.resume("no-such-run", noneJob(calls, fail)));
        assertTrue(unknownRun.error().getMessage().contains("No JobRun found"));
    }

    @Test
    public void onlyFailedOrCancelledRunsCanResume() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean(false);
        JobRunHandle original = jobService.run(noneJob(calls, fail), JobOwner.system());
        assertNull(await(original).error());

        RunResult resumed = await(jobService.resume(original.getJobRunId(), noneJob(calls, fail)));

        assertTrue(resumed.error().getMessage().contains("only FAILED or CANCELLED"));
    }

    private JobDefinition noneJob(AtomicInteger calls, AtomicBoolean fail) {
        return JobDefinition.create("none job")
                .name("none-job").version("1")
                .task(Tasks.fromRunnable("count", calls::incrementAndGet))
                .task(failGate(fail));
    }

    private JobDefinition resultJob(AtomicInteger calls, AtomicBoolean fail) {
        return JobDefinition.create("result job")
                .name("result-job").version("1")
                .taskStoreResult(Tasks.fromCallable("produce widget",
                                                    () -> new Widget("w" + calls.incrementAndGet())))
                .task(failGate(fail));
    }

    private JobDefinition reloadJob(AtomicInteger creates, AtomicInteger reloads, AtomicBoolean fail) {
        return JobDefinition.create("reload job")
                .name("reload-job").version("1")
                .taskStoreResult(Tasks.fromCallable("create widget",
                                                    () -> new Widget("created " + creates.incrementAndGet())),
                                 Tasks.fromCallable("reload widget",
                                                    () -> new Widget("reloaded " + reloads.incrementAndGet())))
                .task(failGate(fail));
    }

    private JobDefinition stateJob(AtomicInteger creates, AtomicBoolean fail,
                                   AtomicReference<WidgetState> seenByLaterTask) {
        return JobDefinition.create("state job")
                .name("state-job").version("1")
                .taskStoreState(Tasks.fromCallable("decide", () -> {
                    creates.incrementAndGet();
                    return new WidgetState("decided");
                }), "widgetState")
                .task(Tasks.fromCallable("observe decision", new java.util.concurrent.Callable<Void>() {

                    @org.springframework.beans.factory.annotation.Autowired
                    private WidgetState widgetState;

                    @Override
                    public Void call() {
                        seenByLaterTask.set(widgetState);
                        return null;
                    }
                }))
                .task(failGate(fail));
    }

    private JobDefinition dynamicJob(AtomicInteger builds, AtomicInteger innerA, AtomicInteger innerB,
                                     AtomicBoolean fail) {
        return JobDefinition.create("dynamic job")
                .name("dynamic-job").version("1")
                .task(Tasks.fromCallable("build inner", () -> {
                    builds.incrementAndGet();
                    return JobDefinition.create("inner")
                            .task(Tasks.fromRunnable("inner a", innerA::incrementAndGet))
                            .task(Tasks.fromRunnable("inner b", innerB::incrementAndGet));
                }))
                .task(failGate(fail));
    }

    private org.kinotic.grindv2.api.model.Task<Void> failGate(AtomicBoolean fail) {
        return Tasks.fromCallable("fail gate", () -> {
            if (fail.get()) {
                throw new IllegalStateException("first run fails here");
            }
            return null;
        });
    }

}
