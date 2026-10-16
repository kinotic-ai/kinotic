package org.kinotic.grind;

import org.kinotic.grind.api.annotations.Task;
import org.kinotic.grind.api.model.TaskIcons;

/**
 * Tasks class declaring an icon on one task and leaving the other at the console's default.
 */
public class IconTasks {

    @Task(order = 1, value = "checkout", icon = TaskIcons.GIT)
    public void checkout() {
    }

    @Task(order = 2, value = "plain")
    public void plain() {
    }

}
