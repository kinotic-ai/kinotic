package org.kinotic.grindv2;

import org.junit.jupiter.api.Test;
import org.kinotic.grindv2.api.ExecutionStatus;
import org.kinotic.grindv2.api.JobDefinition;
import org.kinotic.grindv2.api.JobOwner;
import org.kinotic.grindv2.api.JobRun;
import org.kinotic.grindv2.api.JobRunEvent;
import org.kinotic.grindv2.api.JobRunHandle;
import org.kinotic.grindv2.api.StepCompleted;
import org.kinotic.grindv2.api.StepFailed;
import org.kinotic.grindv2.api.StepStarted;
import org.kinotic.grindv2.api.StepsDiscovered;
import org.kinotic.grindv2.api.StoreType;
import org.kinotic.grindv2.api.Tasks;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The event stream and the persisted ledger: emission order, stored values on completions,
 * record lifecycle, failure capture, laziness, and replay to late subscribers.
 */
public class EventsAndRecordingTest extends AbstractGrindV2Test {

    @Test
    public void emitsLifecycleInOrder() throws Exception {
        JobDefinition job = JobDefinition.create("two steps")
                .name("two-steps").version("1")
                .task(Tasks.fromRunnable("first", () -> { }))
                .task(Tasks.fromRunnable("second", () -> { }));

        RunResult result = await(jobRunner.run(job, JobOwner.system()));

        assertNull(result.error());
        List<JobRunEvent> events = result.events();
        StepsDiscovered discovered = assertInstanceOf(StepsDiscovered.class, events.get(0));
        assertEquals(List.of("0", "0/1", "0/2"),
                     discovered.steps().stream().map(record -> record.getStepPath()).toList());
        assertEquals(List.of(ExecutionStatus.PENDING, ExecutionStatus.PENDING, ExecutionStatus.PENDING),
                     discovered.steps().stream().map(record -> record.getStatus()).toList());
        assertEquals(new StepStarted("0", "two steps"), events.get(1));
        assertEquals(new StepStarted("0/1", "first"), events.get(2));
        assertInstanceOf(StepCompleted.class, events.get(3));
        assertEquals("0/1", events.get(3).stepPath());
        assertEquals(new StepStarted("0/2", "second"), events.get(4));
        assertEquals("0/2", events.get(5).stepPath());
        StepCompleted rootCompleted = assertInstanceOf(StepCompleted.class, events.get(6));
        assertEquals("0", rootCompleted.stepPath());
        assertEquals(7, events.size());
    }

    @Test
    public void completionCarriesStoredValueInProcess() throws Exception {
        JobDefinition job = JobDefinition.create("carrying")
                .name("carrying").version("1")
                .taskStoreResult(Tasks.fromValue("produce widget", new Widget("carried")));

        RunResult result = await(jobRunner.run(job, JobOwner.system()));

        StepCompleted completed = result.events().stream()
                                        .filter(StepCompleted.class::isInstance)
                                        .map(StepCompleted.class::cast)
                                        .filter(event -> event.stepPath().equals("0/1"))
                                        .findFirst().orElseThrow();
        assertEquals(StoreType.RESULT, completed.storeType());
        assertEquals("widget", completed.storedName());
        assertEquals(new Widget("carried"), completed.storedValue());
    }

    @Test
    public void recordsRunAndStepLifecycle() throws Exception {
        JobDefinition job = JobDefinition.create("recorded")
                .name("recorded").version("2.0")
                .task(Tasks.fromRunnable("only", () -> { }));

        JobRunHandle handle = jobRunner.run(job, JobOwner.ofApplication("org1", "app1"));
        await(handle);

        JobRun run = repository.savedRuns.get(handle.getJobRunId());
        assertNotNull(run);
        assertEquals("recorded", run.getName());
        assertEquals("2.0", run.getVersion());
        assertEquals("org1", run.getOrganizationId());
        assertEquals("app1", run.getApplicationId());
        assertEquals(ExecutionStatus.COMPLETED, run.getStatus());
        assertNotNull(run.getStarted());
        assertNotNull(run.getFinished());

        assertEquals(ExecutionStatus.COMPLETED, repository.stepAt(handle.getJobRunId(), "0").getStatus());
        assertEquals(ExecutionStatus.COMPLETED, repository.stepAt(handle.getJobRunId(), "0/1").getStatus());
        assertNotNull(repository.stepAt(handle.getJobRunId(), "0/1").getStarted());
        assertNotNull(repository.stepAt(handle.getJobRunId(), "0/1").getFinished());
    }

    @Test
    public void failureIsRecordedAndUnreachedStepsStayPending() throws Exception {
        IllegalStateException boom = new IllegalStateException("boom");
        JobDefinition job = JobDefinition.create("failing")
                .name("failing").version("1")
                .task(Tasks.fromRunnable("fine", () -> { }))
                .task(Tasks.fromCallable("explodes", () -> { throw boom; }))
                .task(Tasks.fromRunnable("never reached", () -> { }));

        JobRunHandle handle = jobRunner.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertSame(boom, result.error());
        assertTrue(result.events().stream().anyMatch(event -> event instanceof StepFailed failed
                && failed.stepPath().equals("0/2")));

        JobRun run = repository.savedRuns.get(handle.getJobRunId());
        assertEquals(ExecutionStatus.FAILED, run.getStatus());
        assertTrue(run.getError().contains("boom"));
        assertEquals(ExecutionStatus.COMPLETED, repository.stepAt(handle.getJobRunId(), "0/1").getStatus());
        assertEquals(ExecutionStatus.FAILED, repository.stepAt(handle.getJobRunId(), "0/2").getStatus());
        assertEquals(ExecutionStatus.PENDING, repository.stepAt(handle.getJobRunId(), "0/3").getStatus());
        assertEquals(ExecutionStatus.FAILED, repository.stepAt(handle.getJobRunId(), "0").getStatus());
    }

    @Test
    public void nothingExecutesBeforeTheFirstSubscriber() throws Exception {
        AtomicBoolean executed = new AtomicBoolean();
        JobDefinition job = JobDefinition.create("lazy")
                .name("lazy").version("1")
                .task(Tasks.fromRunnable("only", () -> executed.set(true)));

        JobRunHandle handle = jobRunner.run(job, JobOwner.system());
        Thread.sleep(100);
        assertFalse(executed.get());
        assertTrue(repository.savedRuns.isEmpty());

        await(handle);
        assertTrue(executed.get());
    }

    @Test
    public void lateSubscribersReplayTheFullHistory() throws Exception {
        JobDefinition job = JobDefinition.create("replayed")
                .name("replayed").version("1")
                .task(Tasks.fromRunnable("only", () -> { }));

        JobRunHandle handle = jobRunner.run(job, JobOwner.system());
        RunResult first = await(handle);
        RunResult second = await(handle);

        assertEquals(first.events(), second.events());
    }

}
