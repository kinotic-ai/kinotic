package org.kinotic.orchestrator.internal.api.grind;

import jakarta.annotation.PreDestroy;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.grind.ExecutionStatus;
import org.kinotic.domain.api.model.grind.JobRun;
import org.kinotic.domain.api.model.grind.StoreType;
import org.kinotic.domain.api.model.grind.TaskRecord;
import org.kinotic.orchestrator.api.grind.JobDefinition;
import org.kinotic.orchestrator.api.grind.JobExecution;
import org.kinotic.orchestrator.api.grind.Task;
import org.kinotic.orchestrator.api.grind.Tasks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavior of recorded job execution: injection between tasks, run and step records,
 * store tiers, the strict state contract, and multicast execution.
 */
public class JobServiceTest extends AbstractGrindTest {

    private TaskRecord recordFor(String jobRunId, String description) {
        return records.forRun(jobRunId).stream()
                      .filter(record -> description.equals(record.getDescription()))
                      .findFirst()
                      .orElse(null);
    }

    @Test
    public void storedResultsInjectIntoLaterTasks() throws Exception {
        JobDefinition def = JobDefinition.create("injection test").name("injection-test").version("1")
            .taskStoreState(Tasks.fromCallable("make widget", () -> new Widget("hello")))
            .taskStoreState(Tasks.fromCallable("read widget", new Callable<String>() {
                @Autowired
                private Widget widget;
                public String call() { return widget.label + " world"; }
            }), "greeting")
            .task(Tasks.fromCallable("read property", new Callable<String>() {
                @Value("${greeting}")
                private String greeting;
                public String call() { return greeting; }
            }));

        JobExecution execution = jobService.execute(def);
        RunOutcome outcome = await(execution);

        assertFalse(outcome.failed());
        assertEquals(2, outcome.values().stream().filter("hello world"::equals).count(),
                     "@Autowired and @Value should both deliver the produced value");

        JobRun run = runs.saved.get(execution.getJobRunId());
        assertNotNull(run);
        assertEquals("injection-test", run.getName());
        assertEquals("1", run.getVersion());
        assertEquals(ExecutionStatus.COMPLETED, run.getStatus());
        assertNotNull(run.getStarted());
        assertNotNull(run.getFinished());

        // 3 tasks + the root job = 4 records, all COMPLETED
        assertEquals(4, records.forRun(execution.getJobRunId()).size());
        assertTrue(records.forRun(execution.getJobRunId()).stream()
                          .allMatch(record -> record.getStatus() == ExecutionStatus.COMPLETED));

        TaskRecord greeting = recordFor(execution.getJobRunId(), "read widget");
        assertEquals("greeting", greeting.getResultName());
        assertEquals("java.lang.String", greeting.getResultValueType());
        assertEquals("hello world", greeting.getResultValue().asString());
        assertEquals("0/2", greeting.getStepPath());
    }

    @Test
    public void collectionsInjectAsLists() throws Exception {
        JobDefinition def = JobDefinition.create("collection test").name("collection-test")
            .taskStoreResult(Tasks.fromCallable("make widgets", () -> {
                List<Widget> widgets = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    widgets.add(new Widget("w" + i));
                }
                return widgets;
            }))
            .task(Tasks.fromCallable("count widgets", new Callable<Integer>() {
                @Autowired
                private List<Widget> widgets;
                public Integer call() { return widgets.size(); }
            }));

        RunOutcome outcome = await(jobService.execute(def));

        assertFalse(outcome.failed());
        assertTrue(outcome.values().contains(5));
    }

    @Test
    public void childScopeReadsParentStoredProperty() throws Exception {
        JobDefinition child = JobDefinition.create("child job")
            .task(Tasks.fromCallable("read parent prop", new Callable<String>() {
                @Value("${parentProp}")
                private String parentProp;
                public String call() { return parentProp; }
            }));

        JobDefinition def = JobDefinition.create("scope test").name("scope-test")
            .taskStoreResult(Tasks.fromCallable("store in parent", () -> "from-parent"), "parentProp")
            .jobDefinition(child);

        RunOutcome outcome = await(jobService.execute(def));

        assertFalse(outcome.failed());
        assertTrue(outcome.values().contains("from-parent"));
    }

    @Test
    public void preDestroyFiresWhenScopeIsDestroyed() throws Exception {
        AtomicBoolean preDestroyed = new AtomicBoolean(false);
        JobDefinition def = JobDefinition.create("lifecycle test").name("lifecycle-test")
            .task(Tasks.fromRunnable(new Runnable() {
                @PreDestroy
                public void shutdown() { preDestroyed.set(true); }
                public void run() { }
            }));

        await(jobService.execute(def));

        assertTrue(preDestroyed.get());
    }

    @Test
    public void failureMarksStepAndRunFailed() throws Exception {
        JobDefinition def = JobDefinition.create("failure test").name("failure-test")
            .task(Tasks.fromCallable("ok task", () -> "fine"))
            .task(Tasks.fromRunnable("boom task", () -> { throw new IllegalStateException("boom"); }))
            .task(Tasks.fromCallable("never runs", () -> "unreachable"));

        JobExecution execution = jobService.execute(def);
        RunOutcome outcome = await(execution);

        assertTrue(outcome.failed());

        JobRun run = runs.saved.get(execution.getJobRunId());
        assertEquals(ExecutionStatus.FAILED, run.getStatus());
        assertTrue(run.getError().contains("boom"));

        assertEquals(ExecutionStatus.FAILED, recordFor(execution.getJobRunId(), "boom task").getStatus());
        assertEquals(ExecutionStatus.COMPLETED, recordFor(execution.getJobRunId(), "ok task").getStatus());
        assertNull(recordFor(execution.getJobRunId(), "never runs"));
    }

    @Test
    public void multicastExecutesTheJobExactlyOnce() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        JobDefinition def = JobDefinition.create("multicast test").name("multicast-test")
            .task(Tasks.fromCallable("count executions", executions::incrementAndGet));

        JobExecution execution = jobService.execute(def);
        await(execution);
        // a late second subscriber joins the finished run instead of re-executing it
        RunOutcome second = await(execution);

        assertFalse(second.failed());
        assertEquals(1, executions.get());
        assertEquals(1, runs.saved.size());
    }

    @Test
    public void resultTierIsNotPersistedStateTierIs() throws Exception {
        JobDefinition def = JobDefinition.create("store tiers").name("store-tiers")
            .taskStoreResult(Tasks.fromCallable("transient fetch", () -> "ephemeral"), "fetched")
            .taskStoreState(Tasks.fromCallable("durable state", () -> new Widget("keep me")), "kept")
            .task(Tasks.fromCallable("downstream", new Callable<String>() {
                @Value("${fetched}")
                private String fetched;
                @Autowired
                private Widget kept;
                public String call() { return fetched + "/" + kept.label; }
            }));

        JobExecution execution = jobService.execute(def);
        RunOutcome outcome = await(execution);

        assertFalse(outcome.failed());
        assertTrue(outcome.values().contains("ephemeral/keep me"), "both tiers wire the scope");

        TaskRecord fetched = recordFor(execution.getJobRunId(), "transient fetch");
        assertEquals(StoreType.RESULT, fetched.getStoreType());
        assertEquals("fetched", fetched.getResultName());
        assertNull(fetched.getResultValue(), "RESULT values are not persisted");

        TaskRecord kept = recordFor(execution.getJobRunId(), "durable state");
        assertEquals(StoreType.STATE, kept.getStoreType());
        assertNotNull(kept.getResultValue());
        assertTrue(kept.getResultValueType().endsWith("Widget"));
    }

    @Test
    public void parallelJobExecutesAllTasks() throws Exception {
        JobDefinition def = JobDefinition.create("parallel test", true).name("parallel-test");
        for (int i = 1; i <= 5; i++) {
            final int taskNumber = i;
            def.task(Tasks.fromCallable("parallel task " + taskNumber, () -> {
                Thread.sleep(taskNumber * 10L);
                return taskNumber;
            }));
        }

        RunOutcome outcome = await(jobService.execute(def));

        assertFalse(outcome.failed());
        // parallel execution guarantees completion, not order
        for (int i = 1; i <= 5; i++) {
            assertTrue(outcome.values().contains(i), "task " + i + " must have executed");
        }
    }

    @Test
    public void taskReturningTaskExecutesItWithTheStepsStoreSettings() throws Exception {
        JobDefinition def = JobDefinition.create("task supplier").name("task-supplier")
            .taskStoreState(Tasks.fromCallable("make widget", () -> new Widget("deferred")))
            .taskStoreResult(Tasks.fromSupplier("supply task", new Supplier<Task<String>>() {
                @Autowired
                private Widget widget;
                public Task<String> get() { return Tasks.fromValue("supplied value", widget.label + "!"); }
            }), "supplied")
            .task(Tasks.fromCallable("read supplied", new Callable<String>() {
                @Value("${supplied}")
                private String supplied;
                public String call() { return supplied; }
            }));

        RunOutcome outcome = await(jobService.execute(def));

        assertFalse(outcome.failed());
        assertEquals(2, outcome.values().stream().filter("deferred!"::equals).count(),
                     "the returned task's value must be emitted and stored under the declaring step's name");
    }

    @Test
    public void taskReturningJobDefinitionExecutesItsSteps() throws Exception {
        JobDefinition def = JobDefinition.create("sub job").name("sub-job")
            .taskStoreResult(Tasks.fromValue("task count", 3), "taskCount")
            .task(Tasks.fromCallable("build sub job", new Callable<JobDefinition>() {
                @Value("${taskCount}")
                private int taskCount;
                public JobDefinition call() {
                    JobDefinition subJob = JobDefinition.create("generated sub job");
                    for (int i = 1; i <= taskCount; i++) {
                        final int taskNumber = i;
                        subJob.task(Tasks.fromCallable("sub task " + taskNumber, () -> "sub-" + taskNumber));
                    }
                    return subJob;
                }
            }));

        RunOutcome outcome = await(jobService.execute(def));

        assertFalse(outcome.failed());
        for (int i = 1; i <= 3; i++) {
            assertTrue(outcome.values().contains("sub-" + i), "generated step " + i + " must have executed");
        }
    }

    @Test
    public void strictContractRejectsBareCollectionsAsState() throws Exception {
        JobDefinition def = JobDefinition.create("collection state").name("collection-state")
            .taskStoreState(Tasks.fromCallable("bare list", () -> List.of(new Widget("a"), new Widget("b"))), "widgets")
            .task(Tasks.fromCallable("never runs", () -> "unreachable"));

        JobExecution execution = jobService.execute(def);
        RunOutcome outcome = await(execution);

        assertTrue(outcome.failed());
        assertTrue(outcome.error().getMessage().contains("wrap the value in a domain class"),
                   "the failure must point authors at the wrapper pattern");

        TaskRecord bad = recordFor(execution.getJobRunId(), "bare list");
        assertEquals(ExecutionStatus.FAILED, bad.getStatus());
        assertNull(recordFor(execution.getJobRunId(), "never runs"));
    }

    @Test
    public void strictContractRejectsAnyGenericTypeAsState() throws Exception {
        JobDefinition def = JobDefinition.create("optional state").name("optional-state")
            .taskStoreState(Tasks.fromCallable("bare optional", () -> java.util.Optional.of(new Widget("a"))), "maybe");

        RunOutcome outcome = await(jobService.execute(def));

        assertTrue(outcome.failed());
        assertTrue(outcome.error().getMessage().contains("a generic type"));
    }

    @Test
    public void strictContractFailsRunOnUnserializableState() throws Exception {
        class Cyclic {
            @SuppressWarnings("unused")
            public Cyclic self = this;
        }

        JobDefinition def = JobDefinition.create("strict test").name("strict-test")
            .taskStoreState(Tasks.fromCallable("bad state", Cyclic::new), "bad")
            .task(Tasks.fromCallable("never runs", () -> "unreachable"));

        JobExecution execution = jobService.execute(def);
        RunOutcome outcome = await(execution);

        assertTrue(outcome.failed());
        assertTrue(outcome.error().getMessage().contains("not serializable"));

        JobRun run = runs.saved.get(execution.getJobRunId());
        assertEquals(ExecutionStatus.FAILED, run.getStatus());
        assertTrue(run.getError().contains("taskStoreState"));

        TaskRecord bad = recordFor(execution.getJobRunId(), "bad state");
        assertEquals(ExecutionStatus.FAILED, bad.getStatus());
        assertTrue(bad.getError().contains("not serializable"));
        assertNull(recordFor(execution.getJobRunId(), "never runs"));
    }

}
