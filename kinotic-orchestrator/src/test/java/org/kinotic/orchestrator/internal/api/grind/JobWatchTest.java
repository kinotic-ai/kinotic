package org.kinotic.orchestrator.internal.api.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.orchestrator.api.model.grind.JobDefinition;
import org.kinotic.orchestrator.api.model.grind.JobExecution;
import org.kinotic.orchestrator.api.model.grind.JobProgressEvent;
import org.kinotic.orchestrator.api.model.grind.JobProgressEventType;
import org.kinotic.orchestrator.api.model.grind.JobRun;
import org.kinotic.orchestrator.api.model.grind.Tasks;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior of watching a run in flight: what an observer that did not start the run receives,
 * and the boundaries of what is watchable.
 */
public class JobWatchTest extends AbstractGrindTest {

    private List<JobProgressEvent> eventsOf(String jobRunId) {
        List<JobProgressEvent> events = Collections.synchronizedList(new ArrayList<>());
        jobService.watch(jobRunId).subscribe(events::add);
        return events;
    }

    private List<JobProgressEvent> eventsFor(List<JobProgressEvent> events, String stepPath) {
        return events.stream().filter(event -> stepPath.equals(event.getStepPath())).toList();
    }

    /** The step's lifecycle alone, with the progress reports it interleaves them with removed. */
    private List<JobProgressEventType> lifecycleOf(List<JobProgressEvent> events, String stepPath) {
        return eventsFor(events, stepPath).stream()
                                          .map(JobProgressEvent::getType)
                                          .filter(type -> type != JobProgressEventType.STEP_PROGRESS)
                                          .toList();
    }

    /**
     * Runs the execution to termination on its own thread, so the calling test can attach a watch
     * while it is in flight.
     */
    private Thread startOnItsOwnThread(JobExecution execution) {
        Thread runner = new Thread(() -> execution.getResults().onErrorComplete().blockLast());
        runner.start();
        return runner;
    }

    @Test
    public void watcherReceivesTheWholeRunIncludingWhatPrecededIt() throws Exception {
        CountDownLatch firstStepEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstStep = new CountDownLatch(1);

        JobDefinition def = JobDefinition.create("watched job").name("watched-job").version("1")
            .task(Tasks.fromCallable("gated step", () -> {
                firstStepEntered.countDown();
                assertTrue(releaseFirstStep.await(15, TimeUnit.SECONDS), "step was never released");
                return "gated";
            }))
            .task(Tasks.fromCallable("trailing step", () -> "trailing"));

        JobExecution execution = jobService.execute(def);
        Thread runner = startOnItsOwnThread(execution);
        assertTrue(firstStepEntered.await(15, TimeUnit.SECONDS), "run never reached its first step");

        List<JobProgressEvent> events = eventsOf(execution.getJobRunId());

        releaseFirstStep.countDown();
        runner.join(15_000);
        assertFalse(runner.isAlive(), "run did not terminate within 15s");

        // The gated step had already started when the watch attached, and its start still arrives
        assertEquals(List.of(JobProgressEventType.STEP_STARTED, JobProgressEventType.STEP_COMPLETED),
                     lifecycleOf(events, "0/1"));
        assertEquals("gated step", eventsFor(events, "0/1").get(0).getDescription());

        assertEquals(List.of(JobProgressEventType.STEP_STARTED, JobProgressEventType.STEP_COMPLETED),
                     lifecycleOf(events, "0/2"));
        assertEquals("trailing step", eventsFor(events, "0/2").get(0).getDescription());

        // The root job aggregates its steps' progress, so it is what a UI shows for the run overall
        JobProgressEvent finalProgress = eventsFor(events, "0").stream()
                                                               .filter(event -> event.getType() == JobProgressEventType.STEP_PROGRESS)
                                                               .reduce((first, second) -> second)
                                                               .orElse(null);
        assertNotNull(finalProgress, "the root job should report progress");
        assertEquals(100, finalProgress.getPercentageComplete());
    }

    @Test
    public void failedStepIsReportedWithItsFailure() throws Exception {
        CountDownLatch firstStepEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstStep = new CountDownLatch(1);

        JobDefinition def = JobDefinition.create("failing job").name("failing-job").version("1")
            .task(Tasks.fromCallable("gated step", () -> {
                firstStepEntered.countDown();
                assertTrue(releaseFirstStep.await(15, TimeUnit.SECONDS), "step was never released");
                return "gated";
            }))
            .task(Tasks.fromCallable("exploding step", () -> {
                throw new IllegalStateException("boom");
            }));

        JobExecution execution = jobService.execute(def);
        Thread runner = startOnItsOwnThread(execution);
        assertTrue(firstStepEntered.await(15, TimeUnit.SECONDS), "run never reached its first step");

        List<JobProgressEvent> events = eventsOf(execution.getJobRunId());

        releaseFirstStep.countDown();
        runner.join(15_000);
        assertFalse(runner.isAlive(), "run did not terminate within 15s");

        JobProgressEvent failure = events.stream()
                                         .filter(event -> event.getType() == JobProgressEventType.STEP_FAILED)
                                         .findFirst()
                                         .orElse(null);
        assertNotNull(failure, "the failed step should be reported");
        assertEquals("0/2", failure.getStepPath());
        assertTrue(failure.getMessage().contains("boom"), "the failure should carry its message");
    }

    @Test
    public void watchingDoesNotStartTheRun() throws Exception {
        JobDefinition def = JobDefinition.create("unstarted job").name("unstarted-job").version("1")
            .task(Tasks.fromCallable("never runs", () -> "value"));

        JobExecution execution = jobService.execute(def);

        List<JobProgressEvent> events = eventsOf(execution.getJobRunId());

        assertTrue(events.isEmpty(), "a run nobody has subscribed to is not executing yet");
        assertTrue(runs.saved.isEmpty(), "no run should have been recorded");
    }

    @Test
    public void finishedRunIsNoLongerWatchable() throws Exception {
        JobDefinition def = JobDefinition.create("brief job").name("brief-job").version("1")
            .task(Tasks.fromCallable("quick step", () -> "value"));

        JobExecution execution = jobService.execute(def);
        await(execution);

        assertTrue(eventsOf(execution.getJobRunId()).isEmpty(),
                   "a terminated run is read from its records, not watched");

        JobRun run = runs.saved.get(execution.getJobRunId());
        assertEquals(TEST_NODE_ID, run.getNodeId(), "the run records the node that can serve a watch");
    }

    @Test
    public void unknownRunYieldsNothing() {
        Disposable watch = jobService.watch("no-such-run").subscribe();
        assertTrue(watch.isDisposed(), "an unknown run completes immediately");
    }

}
