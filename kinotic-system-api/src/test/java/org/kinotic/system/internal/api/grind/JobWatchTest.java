package org.kinotic.system.internal.api.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.management.api.model.grind.DiagnosticLevel;
import org.kinotic.management.api.model.grind.ExecutionStatus;
import org.kinotic.system.api.model.grind.JobDefinition;
import org.kinotic.system.api.model.grind.JobExecution;
import org.kinotic.management.api.model.grind.Progress;
import org.kinotic.management.api.model.grind.Result;
import org.kinotic.system.api.model.grind.ResultOptions;
import org.kinotic.management.api.model.grind.ResultType;
import org.kinotic.management.api.model.grind.StepCompletion;
import org.kinotic.management.api.model.grind.TaskRecord;
import org.kinotic.system.api.model.grind.Tasks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavior of {@code JobService.watchExecution}: attach only to started runs, full replay for
 * late watchers, and wire-safe result values.
 */
public class JobWatchTest extends AbstractGrindTest {

    /**
     * Subscribes the execution on a background thread so a gate task can block without
     * deadlocking the test thread.
     */
    private void startInBackground(JobExecution execution) {
        execution.getResults()
                 .subscribeOn(Schedulers.boundedElastic())
                 .subscribe(result -> { }, throwable -> { });
    }

    private List<Result<?>> ofType(List<Result<?>> results, ResultType type) {
        return results.stream()
                      .filter(result -> result.getResultType() == type)
                      .toList();
    }

    @Test
    public void watchAttachesMidRunAndReplaysFromTheStart() throws Exception {
        CountDownLatch gateReached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JobDefinition def = JobDefinition.create("watched job").name("watched-job")
            .task(Tasks.fromCallable("gate", () -> {
                gateReached.countDown();
                release.await();
                return "opened";
            }))
            .task(Tasks.fromCallable("after gate", () -> "done"));

        JobExecution execution = jobService.execute(def);
        startInBackground(execution);
        assertTrue(gateReached.await(5, TimeUnit.SECONDS), "gate task did not start");

        List<Result<?>> watched = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch watchTerminal = new CountDownLatch(1);
        jobService.watchExecution(execution.getJobRunId())
                  .subscribe(watched::add, throwable -> watchTerminal.countDown(), watchTerminal::countDown);

        // the gate's STEP_STARTED was emitted before the watcher attached, so it can only come from replay
        assertTrue(ofType(watched, ResultType.STEP_STARTED).stream()
                                                           .anyMatch(result -> "gate".equals(result.getValue())),
                   "watcher must replay results emitted before it attached");

        release.countDown();
        assertTrue(watchTerminal.await(15, TimeUnit.SECONDS), "watch stream did not terminate");
        assertTrue(ofType(watched, ResultType.STEP_STARTED).stream()
                                                           .anyMatch(result -> "after gate".equals(result.getValue())),
                   "watcher must continue receiving live results");
    }

    @Test
    public void watchDeliversWireSafeValues() throws Exception {
        CountDownLatch gateReached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JobDefinition def = JobDefinition.create("sanitized job").name("sanitized-job")
            .task(Tasks.fromCallable("gate", () -> {
                gateReached.countDown();
                release.await();
                return "opened";
            }))
            .taskStoreState(Tasks.fromCallable("make widget", () -> new Widget("stored")), "widget")
            .task(Tasks.fromCallable("generator", () ->
                JobDefinition.create("generated")
                    .task(Tasks.fromCallable("inner", () -> "inner value"))));

        JobExecution execution = jobService.execute(def, new ResultOptions(DiagnosticLevel.NONE, true));
        startInBackground(execution);
        assertTrue(gateReached.await(5, TimeUnit.SECONDS), "gate task did not start");

        List<Result<?>> watched = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch watchTerminal = new CountDownLatch(1);
        jobService.watchExecution(execution.getJobRunId())
                  .subscribe(watched::add, throwable -> watchTerminal.countDown(), watchTerminal::countDown);
        release.countDown();
        assertTrue(watchTerminal.await(15, TimeUnit.SECONDS), "watch stream did not terminate");

        // VALUE results signal timing only - the produced object stays in the executing process
        assertFalse(ofType(watched, ResultType.VALUE).isEmpty());
        assertTrue(ofType(watched, ResultType.VALUE).stream().allMatch(result -> result.getValue() == null));

        // completions keep their store metadata but drop the stored object
        StepCompletion widgetCompletion = ofType(watched, ResultType.STEP_COMPLETED).stream()
                .map(result -> (StepCompletion) result.getValue())
                .filter(completion -> "widget".equals(completion.getStoredName()))
                .findFirst().orElseThrow();
        assertNull(widgetCompletion.getStoredValue());

        // dynamic discoveries arrive as the PENDING records seeded for the revealed subtree
        List<Result<?>> discoveries = ofType(watched, ResultType.DYNAMIC_STEPS);
        assertEquals(1, discoveries.size());
        @SuppressWarnings("unchecked")
        List<TaskRecord> discovered = (List<TaskRecord>) discoveries.getFirst().getValue();
        assertEquals(List.of("0/3/1", "0/3/1/1"),
                     discovered.stream().map(TaskRecord::getStepPath).toList());
        assertTrue(discovered.stream().allMatch(record -> record.getStatus() == ExecutionStatus.PENDING));
        assertEquals("0/3", discoveries.getFirst().getStepInfo().path());

        // progress passes through untouched
        assertTrue(ofType(watched, ResultType.PROGRESS).stream()
                                                       .allMatch(result -> result.getValue() instanceof Progress));
    }

    @Test
    public void watchDeliversFailureAsMessage() throws Exception {
        CountDownLatch gateReached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JobDefinition def = JobDefinition.create("failing job").name("failing-job")
            .task(Tasks.fromCallable("gate", () -> {
                gateReached.countDown();
                release.await();
                return "opened";
            }))
            .task(Tasks.fromRunnable("boom", () -> { throw new IllegalStateException("boom"); }));

        JobExecution execution = jobService.execute(def);
        startInBackground(execution);
        assertTrue(gateReached.await(5, TimeUnit.SECONDS), "gate task did not start");

        List<Result<?>> watched = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> watchError = new AtomicReference<>();
        CountDownLatch watchTerminal = new CountDownLatch(1);
        jobService.watchExecution(execution.getJobRunId())
                  .subscribe(watched::add,
                             throwable -> {
                                 watchError.set(throwable);
                                 watchTerminal.countDown();
                             },
                             watchTerminal::countDown);
        release.countDown();
        assertTrue(watchTerminal.await(15, TimeUnit.SECONDS), "watch stream did not terminate");

        assertNotNull(watchError.get(), "the failure must terminate the watch stream");
        List<Result<?>> failures = ofType(watched, ResultType.STEP_FAILED);
        assertFalse(failures.isEmpty());
        assertTrue(failures.stream().allMatch(result -> result.getValue() instanceof String message
                                                        && message.contains("boom")),
                   "failures must arrive as messages, not Throwables");
    }

    @Test
    public void watchIsEmptyBeforeStartAndAfterCompletion() throws Exception {
        JobDefinition def = JobDefinition.create("unwatched job").name("unwatched-job")
            .task(Tasks.fromCallable("work", () -> "ok"));
        JobExecution execution = jobService.execute(def);

        // before anything subscribes the run has not started, so there is nothing to watch
        List<Result<?>> results = jobService.watchExecution(execution.getJobRunId())
                                            .collectList().block();
        assertTrue(results.isEmpty());
        assertTrue(runs.saved.isEmpty(), "watching must never start the run");

        RunOutcome outcome = await(execution);
        assertFalse(outcome.failed());

        results = jobService.watchExecution(execution.getJobRunId())
                            .collectList().block();
        assertTrue(results.isEmpty(), "a finished run is served from its records, not the registry");
    }

}
