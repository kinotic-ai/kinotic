package org.kinotic.grindv2;

import org.kinotic.grindv2.api.annotations.Task;
import org.kinotic.grindv2.api.model.ProgressReporter;

/**
 * Tasks class reporting progress through an injected method parameter.
 */
public class ProgressTasks {

    @Task(order = 1, value = "measured work")
    public void work(ProgressReporter progress) {
        progress.report(50, "halfway");
    }

}
