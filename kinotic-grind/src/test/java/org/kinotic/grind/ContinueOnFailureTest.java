package org.kinotic.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.JobScope;
import org.kinotic.grind.api.model.Tasks;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Definitions continuing on failure: every task runs, and the run fails with the first failure.
 */
public class ContinueOnFailureTest extends AbstractGrindTest {

    @Test
    public void everyTaskRunsAndTheRunFailsWithTheFirstFailure() throws Exception {
        AtomicBoolean lastRan = new AtomicBoolean();
        JobDefinition job = JobDefinition.create("independent")
                .name("independent").version("1")
                .continueOnFailure()
                .task(Tasks.fromRunnable("first", () -> {
                    throw new IllegalStateException("first failed");
                }))
                .task(Tasks.fromRunnable("second", () -> {
                    throw new IllegalStateException("second failed");
                }))
                .task(Tasks.fromRunnable("third", () -> lastRan.set(true)));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertTrue(lastRan.get(), "the task after the failures did not run");
        assertNotNull(result.error());
        assertTrue(result.error().getMessage().contains("first failed"));
        assertEquals(ExecutionStatus.FAILED, repository.savedRuns.get(handle.getJobRunId()).getStatus());
        assertEquals(ExecutionStatus.FAILED, repository.taskAt(handle.getJobRunId(), "0/1").getStatus());
        assertEquals(ExecutionStatus.FAILED, repository.taskAt(handle.getJobRunId(), "0/2").getStatus());
        assertEquals(ExecutionStatus.COMPLETED, repository.taskAt(handle.getJobRunId(), "0/3").getStatus());
    }

    @Test
    public void aParallelDefinitionCannotContinueOnFailure() {
        JobDefinition parallel = JobDefinition.create("parallel", JobScope.CHILD, true);
        assertThrows(IllegalStateException.class, parallel::continueOnFailure);
    }

}
