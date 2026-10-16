package org.kinotic.grind;

import org.kinotic.grind.api.annotations.Task;
import org.kinotic.grind.api.model.TaskReporter;

/**
 * Tasks class reporting progress through an injected method parameter.
 */
public class ProgressTasks {

    @Task(order = 1, value = "measured work")
    public void work(TaskReporter progress) {
        progress.progress(50, "halfway");
    }

}
