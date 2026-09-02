package org.kinotic.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.events.JobRunEvent;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.events.TaskCompletedEvent;
import org.kinotic.grind.api.model.events.TaskFailedEvent;
import org.kinotic.grind.api.model.events.TaskStartedEvent;
import org.kinotic.grind.api.model.events.TasksDiscoveredEvent;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.StoreType;
import org.kinotic.grind.api.model.Tasks;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The event stream and the persisted ledger: emission order, stored values on completions,
 * record lifecycle, write-ahead recording, failure capture, laziness, and replay to late
 * subscribers.
 */
public class EventsAndRecordingTest extends AbstractGrindTest {

    @Test
    public void emitsLifecycleInOrder() throws Exception {
        JobDefinition job = JobDefinition.create("two tasks")
                .name("two-tasks").version("1")
                .task(Tasks.fromRunnable("first", () -> { }))
                .task(Tasks.fromRunnable("second", () -> { }));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        List<JobRunEvent> events = result.events();
        TasksDiscoveredEvent discovered = assertInstanceOf(TasksDiscoveredEvent.class, events.get(0));
        assertFalse(discovered.dynamic());
        assertEquals(List.of("0", "0/1", "0/2"),
                     discovered.tasks().stream().map(record -> record.getTaskPath()).toList());
        assertEquals(List.of(ExecutionStatus.PENDING, ExecutionStatus.PENDING, ExecutionStatus.PENDING),
                     discovered.tasks().stream().map(record -> record.getStatus()).toList());
        assertEquals(new TaskStartedEvent("0", "two tasks"), events.get(1));
        assertEquals(new TaskStartedEvent("0/1", "first"), events.get(2));
        assertInstanceOf(TaskCompletedEvent.class, events.get(3));
        assertEquals("0/1", events.get(3).taskPath());
        assertEquals(new TaskStartedEvent("0/2", "second"), events.get(4));
        assertEquals("0/2", events.get(5).taskPath());
        TaskCompletedEvent rootCompleted = assertInstanceOf(TaskCompletedEvent.class, events.get(6));
        assertEquals("0", rootCompleted.taskPath());
        assertEquals(7, events.size());
    }

    @Test
    public void completionCarriesStoredValueInProcess() throws Exception {
        JobDefinition job = JobDefinition.create("carrying")
                .name("carrying").version("1")
                .task(Tasks.fromValue("produce widget", new Widget("carried")), Store.result());

        RunResult result = await(jobService.run(job, JobOwner.system()));

        TaskCompletedEvent completed = result.events().stream()
                                        .filter(TaskCompletedEvent.class::isInstance)
                                        .map(TaskCompletedEvent.class::cast)
                                        .filter(event -> event.taskPath().equals("0/1"))
                                        .findFirst().orElseThrow();
        assertEquals(StoreType.RESULT, completed.storeType());
        assertEquals("widget", completed.storedName());
        assertEquals(new Widget("carried"), completed.storedValue());
    }

    @Test
    public void recordsRunAndTaskLifecycle() throws Exception {
        JobDefinition job = JobDefinition.create("recorded")
                .name("recorded").version("2.0")
                .task(Tasks.fromRunnable("only", () -> { }));

        JobRunHandle handle = jobService.run(job, JobOwner.ofApplication("org1", "app1"));
        await(handle);

        JobRun run = repository.savedRuns.get(handle.getJobRunId());
        assertNotNull(run);
        assertEquals("recorded", run.getName());
        assertEquals("2.0", run.getVersion());
        assertEquals("org1", run.getOrganizationId());
        assertEquals("app1", run.getApplicationId());
        assertEquals(TEST_NODE_ID, run.getNodeId());
        assertEquals(ExecutionStatus.COMPLETED, run.getStatus());
        assertNotNull(run.getStarted());
        assertNotNull(run.getFinished());

        assertEquals(ExecutionStatus.COMPLETED, repository.taskAt(handle.getJobRunId(), "0").getStatus());
        assertEquals(ExecutionStatus.COMPLETED, repository.taskAt(handle.getJobRunId(), "0/1").getStatus());
        assertNotNull(repository.taskAt(handle.getJobRunId(), "0/1").getStarted());
        assertNotNull(repository.taskAt(handle.getJobRunId(), "0/1").getFinished());
    }

    @Test
    public void ledgerIsWrittenAheadOfExecution() throws Exception {
        // the run id is known before the run starts, so the tasks can read their own ledger
        AtomicReference<String> runId = new AtomicReference<>();
        AtomicReference<ExecutionStatus> runStatusSeenByFirst = new AtomicReference<>();
        AtomicReference<ExecutionStatus> ownStatusSeenByFirst = new AtomicReference<>();
        AtomicReference<ExecutionStatus> firstStatusSeenBySecond = new AtomicReference<>();
        AtomicReference<ExecutionStatus> ownStatusSeenBySecond = new AtomicReference<>();
        JobDefinition job = JobDefinition.create("write-ahead")
                .name("write-ahead").version("1")
                .task(Tasks.fromRunnable("first", () -> {
                    runStatusSeenByFirst.set(repository.savedRuns.get(runId.get()).getStatus());
                    ownStatusSeenByFirst.set(repository.taskAt(runId.get(), "0/1").getStatus());
                }))
                .task(Tasks.fromRunnable("second", () -> {
                    firstStatusSeenBySecond.set(repository.taskAt(runId.get(), "0/1").getStatus());
                    ownStatusSeenBySecond.set(repository.taskAt(runId.get(), "0/2").getStatus());
                }));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        runId.set(handle.getJobRunId());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(ExecutionStatus.RUNNING, runStatusSeenByFirst.get());
        assertEquals(ExecutionStatus.RUNNING, ownStatusSeenByFirst.get());
        assertEquals(ExecutionStatus.COMPLETED, firstStatusSeenBySecond.get());
        assertEquals(ExecutionStatus.RUNNING, ownStatusSeenBySecond.get());
        // the stream terminates only after the terminal records have landed
        assertEquals(ExecutionStatus.COMPLETED, repository.savedRuns.get(handle.getJobRunId()).getStatus());
    }

    @Test
    public void failureIsRecordedAndUnreachedTasksStayPending() throws Exception {
        IllegalStateException boom = new IllegalStateException("boom");
        JobDefinition job = JobDefinition.create("failing")
                .name("failing").version("1")
                .task(Tasks.fromRunnable("fine", () -> { }))
                .task(Tasks.fromCallable("explodes", () -> { throw boom; }))
                .task(Tasks.fromRunnable("never reached", () -> { }));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertSame(boom, result.error());
        assertTrue(result.events().stream().anyMatch(event -> event instanceof TaskFailedEvent failed
                && failed.taskPath().equals("0/2")));

        JobRun run = repository.savedRuns.get(handle.getJobRunId());
        assertEquals(ExecutionStatus.FAILED, run.getStatus());
        assertTrue(run.getError().contains("boom"));
        assertEquals(ExecutionStatus.COMPLETED, repository.taskAt(handle.getJobRunId(), "0/1").getStatus());
        assertEquals(ExecutionStatus.FAILED, repository.taskAt(handle.getJobRunId(), "0/2").getStatus());
        assertEquals(ExecutionStatus.PENDING, repository.taskAt(handle.getJobRunId(), "0/3").getStatus());
        assertEquals(ExecutionStatus.FAILED, repository.taskAt(handle.getJobRunId(), "0").getStatus());
    }

    @Test
    public void nothingExecutesBeforeTheFirstSubscriber() throws Exception {
        AtomicBoolean executed = new AtomicBoolean();
        JobDefinition job = JobDefinition.create("lazy")
                .name("lazy").version("1")
                .task(Tasks.fromRunnable("only", () -> executed.set(true)));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
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

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult first = await(handle);
        RunResult second = await(handle);

        assertEquals(first.events(), second.events());
    }

}
