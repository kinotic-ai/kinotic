package org.kinotic.grindv2;

import org.junit.jupiter.api.Test;
import org.kinotic.grindv2.api.model.ExecutionStatus;
import org.kinotic.grindv2.api.model.JobDefinition;
import org.kinotic.grindv2.api.model.JobOwner;
import org.kinotic.grindv2.api.model.JobRunHandle;
import org.kinotic.grindv2.api.model.JobScope;
import org.kinotic.grindv2.api.model.Tasks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parallel definitions: children overlap in time, and the first failure cancels the
 * in-flight siblings.
 */
public class ParallelTest extends AbstractGrindV2Test {

    @Test
    public void parallelChildrenOverlap() throws Exception {
        // both children must be inside the barrier at once - sequential execution times out
        CyclicBarrier bothRunning = new CyclicBarrier(2);
        JobDefinition job = JobDefinition.create("overlapping", JobScope.CHILD, true)
                .name("overlapping").version("1")
                .task(Tasks.fromCallable("left", () -> bothRunning.await(5, TimeUnit.SECONDS)))
                .task(Tasks.fromCallable("right", () -> bothRunning.await(5, TimeUnit.SECONDS)));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
    }

    @Test
    public void firstFailureCancelsInFlightSiblings() throws Exception {
        IllegalStateException boom = new IllegalStateException("right side failed");
        JobDefinition job = JobDefinition.create("fail fast", JobScope.CHILD, true)
                .name("fail-fast").version("1")
                .task(Tasks.fromCallable("stuck", CompletableFuture::new))
                .task(Tasks.fromCallable("failing", () -> { throw boom; }));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNotNull(result.error());
        assertTrue(result.error().getMessage().contains("right side failed"));
        awaitUntil("ledger to settle", () ->
                repository.savedRuns.get(handle.getJobRunId()) != null
                        && repository.savedRuns.get(handle.getJobRunId()).getStatus() == ExecutionStatus.FAILED);
        assertEquals(ExecutionStatus.FAILED, repository.stepAt(handle.getJobRunId(), "0/2").getStatus());
        assertEquals(ExecutionStatus.CANCELLED, repository.stepAt(handle.getJobRunId(), "0/1").getStatus());
    }

}
