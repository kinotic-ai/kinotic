package org.kinotic.grindv2;

import org.kinotic.grindv2.api.model.JobDefinition;
import org.kinotic.grindv2.api.annotations.Step;
import org.kinotic.grindv2.api.model.Tasks;

/**
 * A steps class whose step builds dynamic structure at runtime by returning a
 * {@link JobDefinition}.
 */
public class DynamicReturnSteps {

    private final StepProbe probe;

    public DynamicReturnSteps(StepProbe probe) {
        this.probe = probe;
    }

    @Step(order = 1, value = "Expand work")
    public JobDefinition expandWork() {
        JobDefinition inner = JobDefinition.create("expanded");
        for (String name : new String[]{"a", "b"}) {
            inner.task(Tasks.fromRunnable("expanded " + name, () -> probe.record("expanded:" + name)));
        }
        return inner;
    }

}
