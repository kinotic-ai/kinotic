package org.kinotic.grindv2;

import org.kinotic.grindv2.api.annotations.Task;

/**
 * Invalid fixture: the first task consumes a type only the later task produces.
 */
public class ConsumedBeforeProducedTasks {

    @Task(order = 1)
    public void consume(Widget widget) {
    }

    @Task(order = 2)
    public Widget produce() {
        return new Widget("late");
    }

}
