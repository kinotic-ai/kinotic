package org.kinotic.grind;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.Participant;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.Tasks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The run thread's Vert.x integration: every task of a run executes on the run's own
 * virtual-thread context, so context-locals such as the platform SecurityContext's
 * participant resolve inside tasks, and Vert.x futures can be awaited without leaving the
 * task.
 */
public class VertxContextTest extends AbstractGrindTest {

    @Test
    public void tasksShareTheRunsVertxContext() throws Exception {
        AtomicReference<Context> first = new AtomicReference<>();
        AtomicReference<Context> second = new AtomicReference<>();
        JobDefinition job = JobDefinition.create("context sharing")
                .name("context-sharing").version("1")
                .task(Tasks.fromRunnable("capture first", () -> first.set(Vertx.currentContext())))
                .task(Tasks.fromRunnable("capture second", () -> second.set(Vertx.currentContext())));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        assertNotNull(first.get());
        assertSame(first.get(), second.get());
    }

    @Test
    public void participantBoundToTheRunContextFlowsToLaterTasks() throws Exception {
        AtomicReference<Participant> seenByLaterTask = new AtomicReference<>();
        JobDefinition job = JobDefinition.create("participant flow")
                .name("participant-flow").version("1")
                .task(Tasks.fromRunnable("authenticate the run",
                                         () -> securityContext.setParticipant(Vertx.currentContext(),
                                                                              new TestParticipant("grind-job"))))
                .task(Tasks.fromRunnable("call as the run's participant",
                                         () -> seenByLaterTask.set(securityContext.currentParticipant())));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        assertNotNull(seenByLaterTask.get());
        assertEquals("grind-job", seenByLaterTask.get().getId());
    }

    @Test
    public void tasksCanAwaitVertxFuturesOnTheRunThread() throws Exception {
        JobDefinition job = JobDefinition.create("awaiting")
                .name("awaiting").version("1")
                .task(Tasks.fromCallable("await a timer",
                                         // Future.await parks the run's virtual thread until the
                                         // event loop completes the timer
                                         () -> new Widget(vertx.timer(20).map(v -> "ticked").await())),
                      Store.result("widget"));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
    }

    @Test
    public void tasksCanAwaitContextBoundFuturesCompletedOffTheRunThread() throws Exception {
        CompletableFuture<String> completedOffContext = new CompletableFuture<>();
        AtomicBoolean parkedBeforeCompletion = new AtomicBoolean();
        JobDefinition job = JobDefinition.create("off-context completion")
                .name("off-context-completion").version("1")
                .task(Tasks.fromCallable("await a context-bound future", () -> {
                          Thread runThread = Thread.currentThread();
                          Thread completer = new Thread(() -> {
                              parkedBeforeCompletion.set(parked(runThread));
                              completedOffContext.complete("completed off the run context");
                          }, "off-context-completer");
                          completer.start();
                          // what every platform repository and service hands back: a promise
                          // bound to the caller's context, completed by a foreign thread
                          return Future.fromCompletionStage(completedOffContext, Vertx.currentContext());
                      }),
                      Store.result("value"));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertTrue(parkedBeforeCompletion.get(), "the completion must land while the run thread is parked");
        assertNull(result.error());
    }

    /**
     * Spins until the run thread parks, for at most ten seconds.
     */
    private boolean parked(Thread runThread) {
        // a completion that lands before the interpreter registers its listener is dispatched
        // inline by FutureBase.emitResult, which never queues the dispatch this test covers
        long deadline = System.currentTimeMillis() + 10_000;
        while (runThread.getState() != Thread.State.WAITING && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        return runThread.getState() == Thread.State.WAITING;
    }

}
