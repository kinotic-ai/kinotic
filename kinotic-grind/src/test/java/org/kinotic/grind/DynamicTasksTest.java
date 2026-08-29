package org.kinotic.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.events.TasksDiscoveredEvent;
import org.kinotic.grind.api.model.Tasks;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runtime-discovered structure: tasks returning a {@link org.kinotic.grind.api.model.Task} or a
 * {@link JobDefinition}, their records, and their discovery events.
 */
public class DynamicTasksTest extends AbstractGrindTest {

    @Test
    public void taskReturningTaskExecutesIt() throws Exception {
        List<String> seen = new ArrayList<>();
        JobDefinition job = JobDefinition.create("dynamic task")
                .name("dynamic-task").version("1")
                .task(Tasks.fromCallable("decide",
                                         () -> Tasks.fromRunnable("decided work", () -> seen.add("decided"))));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(List.of("decided"), seen);
        assertEquals(ExecutionStatus.COMPLETED, repository.taskAt(handle.getJobRunId(), "0/1/1").getStatus());
        assertTrue(repository.taskAt(handle.getJobRunId(), "0/1").isDynamicTasks());
    }

    @Test
    public void taskReturningJobDefinitionExecutesItsTasks() throws Exception {
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

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(List.of("a", "b"), seen);
        assertEquals(ExecutionStatus.COMPLETED, repository.taskAt(handle.getJobRunId(), "0/1/1").getStatus());
        assertEquals(ExecutionStatus.COMPLETED, repository.taskAt(handle.getJobRunId(), "0/1/1/1").getStatus());
        assertEquals(ExecutionStatus.COMPLETED, repository.taskAt(handle.getJobRunId(), "0/1/1/2").getStatus());

        TasksDiscoveredEvent discovery = result.events().stream()
                                          .filter(TasksDiscoveredEvent.class::isInstance)
                                          .map(TasksDiscoveredEvent.class::cast)
                                          .filter(event -> event.taskPath().equals("0/1"))
                                          .findFirst().orElseThrow();
        assertEquals(List.of("0/1/1", "0/1/1/1", "0/1/1/2"),
                     discovery.tasks().stream().map(record -> record.getTaskPath()).toList());
        assertTrue(discovery.dynamic());
    }

    @Test
    public void noopDeclinesDynamicWork() throws Exception {
        JobDefinition job = JobDefinition.create("declined")
                .name("declined").version("1")
                .task(Tasks.fromCallable("decide", () -> Tasks.noop("nothing to do")));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(ExecutionStatus.COMPLETED, repository.taskAt(handle.getJobRunId(), "0/1/1").getStatus());
    }

}
