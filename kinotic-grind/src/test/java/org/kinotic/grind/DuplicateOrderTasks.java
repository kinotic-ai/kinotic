package org.kinotic.grind;

import org.kinotic.grind.api.annotations.Task;

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
