package org.kinotic.grindv2;

import org.kinotic.grindv2.api.annotations.Task;

/**
 * Invalid fixture: two tasks claim the same order.
 */
public class DuplicateOrderTasks {

    @Task(order = 1)
    public void first() {
    }

    @Task(order = 1)
    public void second() {
    }

}
