package org.kinotic.grind;

import org.kinotic.grind.api.annotations.Task;

/**
 * Fixture whose task consumes a type nothing provides, to observe the runtime error.
 */
public class MissingDependencyTasks {

    @Task(order = 1)
    public void consume(WidgetState state) {
    }

}
