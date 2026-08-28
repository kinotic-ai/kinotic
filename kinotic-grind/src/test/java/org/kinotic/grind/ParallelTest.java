package org.kinotic.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.JobScope;
import org.kinotic.grind.api.model.Tasks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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
public class ParallelTest extends AbstractGrindTest {

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
        CountDownLatch stuckRunning = new CountDownLatch(1);
        JobDefinition job = JobDefinition.create("fail fast", JobScope.CHILD, true)
                .name("fail-fast").version("1")
                .task(Tasks.fromCallable("stuck", () -> {
                    stuckRunning.countDown();
                    return new CompletableFuture<>();
                }))
                .task(Tasks.fromCallable("failing", () -> {
                    // the sibling must be in flight, or the failure has nothing to cancel
                    assertTrue(stuckRunning.await(5, TimeUnit.SECONDS), "stuck task did not start");
                    throw boom;
                }));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNotNull(result.error());
        assertTrue(result.error().getMessage().contains("right side failed"));
        awaitUntil("ledger to settle", () ->
                repository.savedRuns.get(handle.getJobRunId()) != null
                        && repository.savedRuns.get(handle.getJobRunId()).getStatus() == ExecutionStatus.FAILED);
        assertEquals(ExecutionStatus.FAILED, repository.taskAt(handle.getJobRunId(), "0/2").getStatus());
        assertEquals(ExecutionStatus.CANCELLED, repository.taskAt(handle.getJobRunId(), "0/1").getStatus());
    }

}
