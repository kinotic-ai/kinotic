package org.kinotic.grindv2;

import org.kinotic.grindv2.api.Step;

/**
 * Fixture whose step consumes a type nothing provides, to observe the runtime error.
 */
public class MissingDependencySteps {

    @Step(order = 1)
    public void consume(WidgetState state) {
    }

}
