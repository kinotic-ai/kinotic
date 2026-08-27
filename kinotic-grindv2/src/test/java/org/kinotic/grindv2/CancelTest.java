package org.kinotic.grindv2;

import org.junit.jupiter.api.Test;
import org.kinotic.grindv2.api.ExecutionStatus;
import org.kinotic.grindv2.api.JobDefinition;
import org.kinotic.grindv2.api.JobOwner;
import org.kinotic.grindv2.api.JobRunHandle;
import org.kinotic.grindv2.api.Tasks;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cancellation: the run and its in-flight steps are recorded CANCELLED, completed steps keep
 * their outcome.
 */
public class CancelTest extends AbstractGrindV2Test {

    @Test
    public void cancelMarksRunAndInFlightStepCancelled() throws Exception {
        JobDefinition job = JobDefinition.create("cancellable")
                .name("cancellable").version("1")
                .task(Tasks.fromRunnable("quick", () -> { }))
                .task(Tasks.fromCallable("stuck", CompletableFuture::new));

        JobRunHandle handle = jobRunner.run(job, JobOwner.system());
        handle.getEvents().subscribe(event -> { }, error -> { });

        awaitUntil("the stuck step to start", () -> {
            var record = repository.stepAt(handle.getJobRunId(), "0/2");
            return record != null && record.getStatus() == ExecutionStatus.RUNNING;
        });

        handle.cancel();

        awaitUntil("the run to record CANCELLED", () -> {
            var run = repository.savedRuns.get(handle.getJobRunId());
            return run != null && run.getStatus() == ExecutionStatus.CANCELLED;
        });
        assertEquals(ExecutionStatus.COMPLETED, repository.stepAt(handle.getJobRunId(), "0/1").getStatus());
        assertEquals(ExecutionStatus.CANCELLED, repository.stepAt(handle.getJobRunId(), "0/2").getStatus());
        assertEquals(ExecutionStatus.CANCELLED, repository.stepAt(handle.getJobRunId(), "0").getStatus());
    }

}
