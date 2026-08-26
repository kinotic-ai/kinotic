package org.kinotic.system.internal.api.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.management.api.model.grind.ExecutionStatus;
import org.kinotic.management.api.model.grind.JobRun;
import org.kinotic.management.api.model.grind.TaskRecord;
import org.kinotic.system.api.model.grind.JobDefinition;
import org.kinotic.system.api.model.grind.JobExecution;
import org.kinotic.system.api.model.grind.JobScope;
import org.kinotic.system.api.model.grind.Tasks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavior of resumed runs: state round-tripping, replay of completed state, reload of
 * transient results, and regeneration of dynamic steps.
 */
public class JobResumeTest extends AbstractGrindTest {

    /**
     * Runs the definition built by {@code definitionBuilder} to its (expected) failure,
     * then flips the failure off and resumes.
     */
    private JobExecution failThenResume(Supplier<JobDefinition> definitionBuilder,
                                        AtomicBoolean shouldFail) throws Exception {
        shouldFail.set(true);
        JobExecution firstRun = jobService.execute(definitionBuilder.get());
        RunOutcome firstOutcome = await(firstRun);
        assertTrue(firstOutcome.failed(), "first run should fail");

        shouldFail.set(false);
        return jobService.resume(firstRun.getJobRunId(), definitionBuilder.get());
    }

    @Test
    public void stateValuesRoundTripThroughTheRecord() throws Exception {
        Widget original = new Widget("round trip me");
        JobDefinition def = JobDefinition.create("round trip").name("round-trip")
            .taskStoreState(Tasks.fromCallable("store widget", () -> original), "widget");

        JobExecution execution = jobService.execute(def);
        await(execution);

        TaskRecord record = records.forRun(execution.getJobRunId()).stream()
                                   .filter(r -> "widget".equals(r.getResultName()))
                                   .findFirst().orElseThrow();

        // the exact read path the replay ledger uses
        Class<?> type = Class.forName(record.getResultValueType());
        Object restored = objectMapper.treeToValue(record.getResultValue(), type);

        assertEquals(Widget.class, restored.getClass());
        assertEquals(original, restored);
    }

    @Test
    public void wrappedCollectionsRoundTripAndReplay() throws Exception {
        AtomicInteger batchExecutions = new AtomicInteger();
        AtomicBoolean shouldFail = new AtomicBoolean();
        WidgetBatch original = new WidgetBatch(java.util.List.of(new Widget("a"), new Widget("b")));

        Supplier<JobDefinition> builder = () -> JobDefinition.create("batch resume").name("batch-resume")
            .taskStoreState(Tasks.fromCallable("make batch", () -> {
                batchExecutions.incrementAndGet();
                return original;
            }), "batch")
            .task(Tasks.fromCallable("flaky", () -> {
                if (shouldFail.get()) {
                    throw new IllegalStateException("first attempt fails");
                }
                return "ok";
            }))
            .task(Tasks.fromCallable("downstream", new Callable<Integer>() {
                @Autowired
                private WidgetBatch batch;
                public Integer call() {
                    // proves elements deserialized as Widgets, not Maps
                    return batch.widgets.get(0) instanceof Widget ? batch.widgets.size() : -1;
                }
            }));

        JobExecution resumed = failThenResume(builder, shouldFail);
        RunOutcome outcome = await(resumed);

        assertFalse(outcome.failed());
        assertEquals(1, batchExecutions.get(), "the wrapped batch replays instead of re-executing");
        assertTrue(outcome.values().contains(2), "elements must restore with their real types");
    }

    @Test
    public void resumeReplaysCompletedStateWithoutReExecuting() throws Exception {
        AtomicInteger stateExecutions = new AtomicInteger();
        AtomicBoolean shouldFail = new AtomicBoolean();

        Supplier<JobDefinition> builder = () -> JobDefinition.create("state resume").name("state-resume")
            .taskStoreState(Tasks.fromCallable("make widget", () -> {
                stateExecutions.incrementAndGet();
                return new Widget("checkpointed");
            }))
            .task(Tasks.fromCallable("flaky", () -> {
                if (shouldFail.get()) {
                    throw new IllegalStateException("first attempt fails");
                }
                return "ok";
            }))
            .task(Tasks.fromCallable("downstream", new Callable<String>() {
                @Autowired
                private Widget widget;
                public String call() { return widget.label; }
            }));

        JobExecution resumed = failThenResume(builder, shouldFail);
        RunOutcome outcome = await(resumed);

        assertFalse(outcome.failed());
        assertEquals(1, stateExecutions.get(), "completed STATE step must not re-execute");
        assertTrue(outcome.values().contains("checkpointed"), "downstream must be wired from the replayed value");

        JobRun resumedRun = records.savedJobRuns.get(resumed.getJobRunId());
        assertEquals(ExecutionStatus.COMPLETED, resumedRun.getStatus());
        assertNotNull(resumedRun.getResumedFrom(), "resumed run must reference the original");
        assertEquals(2, records.savedJobRuns.size());
    }

    @Test
    public void resumeRunsReloadInsteadOfCreateForCompletedPair() throws Exception {
        AtomicInteger createExecutions = new AtomicInteger();
        AtomicInteger reloadExecutions = new AtomicInteger();
        AtomicBoolean shouldFail = new AtomicBoolean();

        Supplier<JobDefinition> builder = () -> JobDefinition.create("create reload").name("create-reload")
            .taskStoreResult(Tasks.fromCallable("create thing", () -> {
                                 createExecutions.incrementAndGet();
                                 return "created";
                             }),
                             Tasks.fromCallable("reload thing", () -> {
                                 reloadExecutions.incrementAndGet();
                                 return "reloaded";
                             }),
                             "thing")
            .task(Tasks.fromCallable("flaky", () -> {
                if (shouldFail.get()) {
                    throw new IllegalStateException("first attempt fails");
                }
                return "ok";
            }))
            .task(Tasks.fromCallable("downstream", new Callable<String>() {
                @Value("${thing}")
                private String thing;
                public String call() { return thing; }
            }));

        JobExecution resumed = failThenResume(builder, shouldFail);
        RunOutcome outcome = await(resumed);

        assertFalse(outcome.failed());
        assertEquals(1, createExecutions.get(), "creation must never repeat");
        assertEquals(1, reloadExecutions.get(), "reload must run instead");
        assertTrue(outcome.values().contains("reloaded"), "downstream must be wired from the reloaded value");
    }

    @Test
    public void resumeReExecutesCompletedTransientFetch() throws Exception {
        AtomicInteger fetchExecutions = new AtomicInteger();
        AtomicBoolean shouldFail = new AtomicBoolean();

        Supplier<JobDefinition> builder = () -> JobDefinition.create("fetch resume").name("fetch-resume")
            .taskStoreResult(Tasks.fromCallable("fetch", () -> "fetched-" + fetchExecutions.incrementAndGet()), "fetched")
            .task(Tasks.fromCallable("flaky", () -> {
                if (shouldFail.get()) {
                    throw new IllegalStateException("first attempt fails");
                }
                return "ok";
            }));

        JobExecution resumed = failThenResume(builder, shouldFail);
        RunOutcome outcome = await(resumed);

        assertFalse(outcome.failed());
        assertEquals(2, fetchExecutions.get(), "a single-arg RESULT store is its own reload and re-runs");
    }

    @Test
    public void resumeSkipsCompletedStepsThatStoreNothing() throws Exception {
        AtomicInteger sideEffects = new AtomicInteger();
        AtomicBoolean shouldFail = new AtomicBoolean();

        Supplier<JobDefinition> builder = () -> JobDefinition.create("skip resume").name("skip-resume")
            .task(Tasks.fromRunnable("side effect", sideEffects::incrementAndGet))
            .task(Tasks.fromCallable("flaky", () -> {
                if (shouldFail.get()) {
                    throw new IllegalStateException("first attempt fails");
                }
                return "ok";
            }));

        JobExecution resumed = failThenResume(builder, shouldFail);
        RunOutcome outcome = await(resumed);

        assertFalse(outcome.failed());
        assertEquals(1, sideEffects.get(), "a completed no-store step must not re-execute");
    }

    @Test
    public void resumeRegeneratesDynamicStepsAndReplaysTheirState() throws Exception {
        AtomicInteger generatorExecutions = new AtomicInteger();
        AtomicInteger innerExecutions = new AtomicInteger();
        AtomicBoolean shouldFail = new AtomicBoolean();

        Supplier<JobDefinition> builder = () -> JobDefinition.create("dynamic resume").name("dynamic-resume")
            .task(Tasks.fromCallable("generator", () -> {
                generatorExecutions.incrementAndGet();
                // PARENT scope so the generated step's stored state lands in the enclosing scope
                return JobDefinition.create("generated", JobScope.PARENT)
                    .taskStoreState(Tasks.fromCallable("inner state", () -> {
                        innerExecutions.incrementAndGet();
                        return new Widget("inner");
                    }), "innerWidget");
            }))
            .task(Tasks.fromCallable("flaky", () -> {
                if (shouldFail.get()) {
                    throw new IllegalStateException("first attempt fails");
                }
                return "ok";
            }))
            .task(Tasks.fromCallable("downstream", new Callable<String>() {
                @Autowired
                private Widget innerWidget;
                public String call() { return innerWidget.label; }
            }));

        JobExecution resumed = failThenResume(builder, shouldFail);
        RunOutcome outcome = await(resumed);

        assertFalse(outcome.failed());
        assertEquals(2, generatorExecutions.get(), "the generator re-executes to regenerate its steps");
        assertEquals(1, innerExecutions.get(), "the regenerated STATE step replays instead of re-executing");
        assertTrue(outcome.values().contains("inner"));
    }

    @Test
    public void resumedRunInheritsTheOriginalRunsOwner() throws Exception {
        AtomicBoolean shouldFail = new AtomicBoolean(true);
        Supplier<JobDefinition> builder = () -> JobDefinition.create("owned resume").name("owned-resume")
            .task(Tasks.fromCallable("flaky", () -> {
                if (shouldFail.get()) {
                    throw new IllegalStateException("first attempt fails");
                }
                return "ok";
            }));

        JobExecution firstRun = jobService.execute(builder.get(),
                                                   org.kinotic.management.api.model.grind.JobOwner.ofApplication("org-1", "app-1"));
        await(firstRun);

        shouldFail.set(false);
        // resume takes no owner - it comes from the original run
        JobExecution resumed = jobService.resume(firstRun.getJobRunId(), builder.get());
        RunOutcome outcome = await(resumed);

        assertFalse(outcome.failed());
        JobRun resumedRun = records.savedJobRuns.get(resumed.getJobRunId());
        assertEquals("org-1", resumedRun.getOrganizationId());
        assertEquals("app-1", resumedRun.getApplicationId());
        assertNull(resumedRun.getProjectId());
    }

    @Test
    public void resumeRefusesACompletedRun() throws Exception {
        JobDefinition def = JobDefinition.create("done").name("done-job")
            .task(Tasks.fromCallable("fine", () -> "ok"));

        JobExecution execution = jobService.execute(def);
        await(execution);

        RunOutcome outcome = await(jobService.resume(execution.getJobRunId(),
                                                     JobDefinition.create("done").name("done-job")
                                                                  .task(Tasks.fromCallable("fine", () -> "ok"))));

        assertTrue(outcome.failed());
        assertTrue(outcome.error().getMessage().contains("only FAILED or CANCELLED"));
    }

    @Test
    public void resumeRefusesANameMismatch() throws Exception {
        JobDefinition def = JobDefinition.create("original").name("original-job")
            .task(Tasks.fromRunnable("boom", () -> { throw new IllegalStateException("boom"); }));

        JobExecution execution = jobService.execute(def);
        await(execution);

        RunOutcome outcome = await(jobService.resume(execution.getJobRunId(),
                                                     JobDefinition.create("other").name("other-job")
                                                                  .task(Tasks.fromCallable("fine", () -> "ok"))));

        assertTrue(outcome.failed());
        assertTrue(outcome.error().getMessage().contains("does not match"));
    }

}
