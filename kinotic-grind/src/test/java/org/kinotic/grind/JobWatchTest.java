package org.kinotic.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.grind.api.model.events.JobRunEvent;
import org.kinotic.grind.api.model.events.TaskFailedEvent;
import org.kinotic.grind.api.model.events.TaskStartedEvent;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior of {@code JobService.watchRun}: it attaches only to runs that have started, a
 * late watcher replays the run from the beginning, and the stream carries the run's failure.
 */
public class JobWatchTest extends AbstractGrindTest {

    @Test
    public void watchAttachesMidRunAndReplaysFromTheStart() throws Exception {
        CountDownLatch gateReached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JobDefinition job = JobDefinition.create("watched job")
                .name("watched-job").version("1")
                .task(Tasks.fromCallable("gate", () -> {
                    gateReached.countDown();
                    release.await();
                    return "opened";
                }))
                .task(Tasks.fromCallable("after gate", () -> "done"));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        startInBackground(handle);
        assertTrue(gateReached.await(5, TimeUnit.SECONDS), "gate task did not start");

        List<JobRunEvent> watched = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch watchTerminal = new CountDownLatch(1);
        jobService.watchRun(handle.getJobRunId())
                  .subscribe(watched::add, throwable -> watchTerminal.countDown(), watchTerminal::countDown);

        // the gate's TaskStartedEvent was emitted before the watcher attached, so it can only come from replay
        assertTrue(startedDescriptions(watched).contains("gate"),
                   "watcher must replay events emitted before it attached");

        release.countDown();
        assertTrue(watchTerminal.await(15, TimeUnit.SECONDS), "watch stream did not terminate");
        assertTrue(startedDescriptions(watched).contains("after gate"),
                   "watcher must continue receiving live events");
    }

    @Test
    public void watchDeliversTheRunFailure() throws Exception {
        CountDownLatch gateReached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JobDefinition job = JobDefinition.create("failing job")
                .name("failing-job").version("1")
                .task(Tasks.fromCallable("gate", () -> {
                    gateReached.countDown();
                    release.await();
                    return "opened";
                }))
                .task(Tasks.fromRunnable("boom", () -> { throw new IllegalStateException("boom"); }));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        startInBackground(handle);
        assertTrue(gateReached.await(5, TimeUnit.SECONDS), "gate task did not start");

        List<JobRunEvent> watched = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> watchError = new AtomicReference<>();
        CountDownLatch watchTerminal = new CountDownLatch(1);
        jobService.watchRun(handle.getJobRunId())
                  .subscribe(watched::add,
                             throwable -> {
                                 watchError.set(throwable);
                                 watchTerminal.countDown();
                             },
                             watchTerminal::countDown);
        release.countDown();
        assertTrue(watchTerminal.await(15, TimeUnit.SECONDS), "watch stream did not terminate");

        assertNotNull(watchError.get(), "the failure must terminate the watch stream");
        assertTrue(watched.stream().anyMatch(event -> event instanceof TaskFailedEvent failed
                                                      && failed.error().contains("boom")));
    }

    @Test
    public void watchIsEmptyBeforeStartAndAfterCompletion() throws Exception {
        JobDefinition job = JobDefinition.create("unwatched job")
                .name("unwatched-job").version("1")
                .task(Tasks.fromCallable("work", () -> "ok"));
        JobRunHandle handle = jobService.run(job, JobOwner.system());

        // before anything subscribes the run has not started, so there is nothing to watch
        assertTrue(jobService.watchRun(handle.getJobRunId()).collectList().block().isEmpty());
        assertTrue(repository.savedRuns.isEmpty(), "watching must never start the run");

        RunResult result = await(handle);
        assertFalse(result.events().isEmpty());

        // deregistration trails the terminal event, so the registry clears just after the run ends
        awaitUntil("the finished run to leave the active registry",
                   () -> jobService.watchRun(handle.getJobRunId()).collectList().block().isEmpty());
    }

    /**
     * Subscribes the run on a background thread so a gate task can block without deadlocking
     * the test thread.
     */
    private void startInBackground(JobRunHandle handle) {
        handle.getEvents()
              .subscribeOn(Schedulers.boundedElastic())
              .subscribe(event -> { }, throwable -> { });
    }

    private List<String> startedDescriptions(List<JobRunEvent> events) {
        return events.stream()
                     .filter(event -> event instanceof TaskStartedEvent)
                     .map(event -> ((TaskStartedEvent) event).description())
                     .toList();
    }

}
