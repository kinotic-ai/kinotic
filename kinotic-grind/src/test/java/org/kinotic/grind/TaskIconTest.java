package org.kinotic.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.TaskIcons;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.grind.api.model.events.TasksDiscoveredEvent;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Icons are facts of the definition: a task's or a nested definition's icon is on its record from
 * discovery, and on the discovery event watchers receive.
 */
public class TaskIconTest extends AbstractGrindTest {

    @Test
    public void iconsAreRecordedFromDiscovery() throws Exception {
        JobDefinition job = JobDefinition.create("iconic")
                .name("iconic").version("1")
                .task(Tasks.fromValue("checkout", "sha").icon(TaskIcons.GIT))
                .jobDefinition(JobDefinition.create("publish").icon(TaskIcons.GLOBE)
                                            .task(Tasks.noop("upload")))
                .task(Tasks.noop("plain"));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        TasksDiscoveredEvent discovered = (TasksDiscoveredEvent) result.events().getFirst();
        Map<String, String> discoveredIcons = discovered.tasks().stream()
                                                        .filter(record -> record.getIcon() != null)
                                                        .collect(Collectors.toMap(TaskRecord::getTaskPath, TaskRecord::getIcon));
        assertEquals(Map.of("0/1", TaskIcons.GIT, "0/2", TaskIcons.GLOBE), discoveredIcons);
        assertEquals(TaskIcons.GIT, repository.taskAt(handle.getJobRunId(), "0/1").getIcon());
        assertEquals(TaskIcons.GLOBE, repository.taskAt(handle.getJobRunId(), "0/2").getIcon());
        assertNull(repository.taskAt(handle.getJobRunId(), "0/3").getIcon());
    }

    @Test
    public void aDynamicTaskIsRecordedWithItsIcon() throws Exception {
        JobDefinition job = JobDefinition.create("dynamic icon")
                .name("dynamic-icon").version("1")
                .task(Tasks.fromCallable("decide", () -> Tasks.noop("decided").icon(TaskIcons.SERVER)));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(TaskIcons.SERVER, repository.taskAt(handle.getJobRunId(), "0/1/1").getIcon());
    }

    @Test
    public void aTaskClassDeclaresIconsOnItsMethods() throws Exception {
        JobDefinition job = JobDefinition.fromTasks(IconTasks.class).name("icon-tasks").version("1");

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(TaskIcons.GIT, repository.taskAt(handle.getJobRunId(), "0/1").getIcon());
        assertNull(repository.taskAt(handle.getJobRunId(), "0/2").getIcon());
    }

    @Test
    public void transformingATaskKeepsItsIcon() {
        assertEquals(TaskIcons.PACKAGE,
                     Tasks.transformResult(Tasks.fromValue("count", 1).icon(TaskIcons.PACKAGE), count -> count + 1).getIcon());
    }

}
