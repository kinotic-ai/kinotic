package org.kinotic.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.Tasks;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cancellation: the run and its in-flight tasks are recorded CANCELLED, completed tasks keep
 * their outcome.
 */
public class CancelTest extends AbstractGrindTest {

    @Test
    public void cancelMarksRunAndInFlightTaskCancelled() throws Exception {
        JobDefinition job = JobDefinition.create("cancellable")
                .name("cancellable").version("1")
                .task(Tasks.fromRunnable("quick", () -> { }))
                .task(Tasks.fromCallable("stuck", CompletableFuture::new));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        handle.getEvents().subscribe(event -> { }, error -> { });

        awaitUntil("the stuck task to start", () -> {
            var record = repository.taskAt(handle.getJobRunId(), "0/2");
            return record != null && record.getStatus() == ExecutionStatus.RUNNING;
        });

        handle.cancel();

        awaitUntil("the run to record CANCELLED", () -> {
            var run = repository.savedRuns.get(handle.getJobRunId());
            return run != null && run.getStatus() == ExecutionStatus.CANCELLED;
        });
        assertEquals(ExecutionStatus.COMPLETED, repository.taskAt(handle.getJobRunId(), "0/1").getStatus());
        assertEquals(ExecutionStatus.CANCELLED, repository.taskAt(handle.getJobRunId(), "0/2").getStatus());
        assertEquals(ExecutionStatus.CANCELLED, repository.taskAt(handle.getJobRunId(), "0").getStatus());
    }

}
