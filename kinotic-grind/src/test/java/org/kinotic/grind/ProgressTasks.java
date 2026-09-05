package org.kinotic.grind;

import org.kinotic.grind.api.annotations.Task;
import org.kinotic.grind.api.model.ProgressReporter;

/**
 * Tasks class reporting progress through an injected method parameter.
 */
public class ProgressTasks {

    @Task(order = 1, value = "measured work")
    public void work(ProgressReporter progress) {
        progress.report(50, "halfway");
    }

}
