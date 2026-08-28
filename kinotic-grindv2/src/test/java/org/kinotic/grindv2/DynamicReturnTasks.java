package org.kinotic.grindv2;

import org.kinotic.grindv2.api.model.JobDefinition;
import org.kinotic.grindv2.api.annotations.Task;
import org.kinotic.grindv2.api.model.Tasks;

/**
 * A tasks class whose task builds dynamic structure at runtime by returning a
 * {@link JobDefinition}.
 */
public class DynamicReturnTasks {

    private final TaskProbe probe;

    public DynamicReturnTasks(TaskProbe probe) {
        this.probe = probe;
    }

    @Task(order = 1, value = "Expand work")
    public JobDefinition expandWork() {
        JobDefinition inner = JobDefinition.create("expanded");
        for (String name : new String[]{"a", "b"}) {
            inner.task(Tasks.fromRunnable("expanded " + name, () -> probe.record("expanded:" + name)));
        }
        return inner;
    }

}
