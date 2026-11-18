package org.kinotic.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.Tasks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Who may start a run: a task's call to the job service is refused and fails the task, from its
 * body or from a callback of one of its futures, while a caller reacting to a run's end starts
 * the next one.
 */
public class StartedByTaskTest extends AbstractGrindTest {

    @Test
    public void aTaskThatStartsARunFailsItsOwnRun() throws Exception {
        JobDefinition job = JobDefinition.create("starter")
                                         .name("starter").version("1")
                                         .task(Tasks.fromCallable("start another",
                                                                  () -> jobService.run(quickJob(), JobOwner.system())));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertInstanceOf(IllegalStateException.class, result.error());
        assertEquals(1, repository.savedRuns.size());
    }

    @Test
    public void aTaskThatResumesARunFailsItsOwnRun() throws Exception {
        JobRunHandle ended = jobService.run(quickJob(), JobOwner.system());
        assertNull(await(ended).error());
        JobDefinition job = JobDefinition.create("resumer")
                                         .name("resumer").version("1")
                                         .task(Tasks.fromCallable("resume another",
                                                                  () -> jobService.resume(ended.getJobRunId(), quickJob())));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertInstanceOf(IllegalStateException.class, result.error());
        assertEquals(2, repository.savedRuns.size());
    }

    @Test
    public void aCallbackOfATasksFutureIsRefused() throws Exception {
        JobDefinition job = JobDefinition.create("starter")
                                         .name("starter").version("1")
                                         .task(Tasks.fromCallable("start another later",
                                                                  () -> vertx.timer(10)
                                                                             .map(id -> jobService.run(quickJob(), JobOwner.system()))));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertInstanceOf(IllegalStateException.class, result.error());
        assertEquals(1, repository.savedRuns.size());
    }

    @Test
    public void aRunStartedWhenAnotherEndsIsAccepted() throws Exception {
        CompletableFuture<JobRunHandle> next = new CompletableFuture<>();
        // the callback runs on the first run's thread, as the scheduler's does when it chains two jobs
        jobService.run(quickJob(), JobOwner.system())
                  .completion()
                  .onComplete(ended -> {
                      try {
                          next.complete(jobService.run(quickJob(), JobOwner.system()));
                      } catch (RuntimeException e) {
                          next.completeExceptionally(e);
                      }
                  });

        assertNull(await(next.get(15, TimeUnit.SECONDS)).error());
        assertEquals(2, repository.savedRuns.size());
    }

    private static JobDefinition quickJob() {
        return JobDefinition.create("quick")
                            .name("quick").version("1")
                            .task(Tasks.fromRunnable("quick", () -> { }));
    }

}
