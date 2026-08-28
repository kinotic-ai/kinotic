package org.kinotic.grindv2;

import org.kinotic.grindv2.api.annotations.Task;

/**
 * Fixture whose task consumes a type nothing provides, to observe the runtime error.
 */
public class MissingDependencyTasks {

    @Task(order = 1)
    public void consume(WidgetState state) {
    }

}
