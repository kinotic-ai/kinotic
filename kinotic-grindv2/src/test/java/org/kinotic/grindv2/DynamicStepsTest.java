package org.kinotic.grindv2;

import org.junit.jupiter.api.Test;
import org.kinotic.grindv2.api.ExecutionStatus;
import org.kinotic.grindv2.api.JobDefinition;
import org.kinotic.grindv2.api.JobOwner;
import org.kinotic.grindv2.api.JobRunHandle;
import org.kinotic.grindv2.api.StepsDiscovered;
import org.kinotic.grindv2.api.Tasks;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runtime-discovered structure: tasks returning a {@link org.kinotic.grindv2.api.Task} or a
 * {@link JobDefinition}, their records, and their discovery events.
 */
public class DynamicStepsTest extends AbstractGrindV2Test {

    @Test
    public void taskReturningTaskExecutesIt() throws Exception {
        List<String> seen = new ArrayList<>();
        JobDefinition job = JobDefinition.create("dynamic task")
                .name("dynamic-task").version("1")
                .task(Tasks.fromCallable("decide",
                                         () -> Tasks.fromRunnable("decided work", () -> seen.add("decided"))));

        JobRunHandle handle = jobRunner.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(List.of("decided"), seen);
        assertEquals(ExecutionStatus.COMPLETED, repository.stepAt(handle.getJobRunId(), "0/1/1").getStatus());
        assertTrue(repository.stepAt(handle.getJobRunId(), "0/1").isDynamicSteps());
    }

    @Test
    public void taskReturningJobDefinitionExecutesItsSteps() throws Exception {
        List<String> seen = new ArrayList<>();
        JobDefinition job = JobDefinition.create("dynamic definition")
                .name("dynamic-definition").version("1")
                .task(Tasks.fromCallable("build inner job", () -> {
                    JobDefinition inner = JobDefinition.create("inner");
                    for (String name : List.of("a", "b")) {
                        inner.task(Tasks.fromRunnable("inner " + name, () -> seen.add(name)));
                    }
                    return inner;
                }));

        JobRunHandle handle = jobRunner.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(List.of("a", "b"), seen);
        assertEquals(ExecutionStatus.COMPLETED, repository.stepAt(handle.getJobRunId(), "0/1/1").getStatus());
        assertEquals(ExecutionStatus.COMPLETED, repository.stepAt(handle.getJobRunId(), "0/1/1/1").getStatus());
        assertEquals(ExecutionStatus.COMPLETED, repository.stepAt(handle.getJobRunId(), "0/1/1/2").getStatus());

        StepsDiscovered discovery = result.events().stream()
                                          .filter(StepsDiscovered.class::isInstance)
                                          .map(StepsDiscovered.class::cast)
                                          .filter(event -> event.stepPath().equals("0/1"))
                                          .findFirst().orElseThrow();
        assertEquals(List.of("0/1/1", "0/1/1/1", "0/1/1/2"),
                     discovery.steps().stream().map(record -> record.getStepPath()).toList());
    }

    @Test
    public void noopDeclinesDynamicWork() throws Exception {
        JobDefinition job = JobDefinition.create("declined")
                .name("declined").version("1")
                .task(Tasks.fromCallable("decide", () -> Tasks.noop("nothing to do")));

        JobRunHandle handle = jobRunner.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(ExecutionStatus.COMPLETED, repository.stepAt(handle.getJobRunId(), "0/1/1").getStatus());
    }

}
