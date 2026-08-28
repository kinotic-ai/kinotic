package org.kinotic.grindv2;

import org.kinotic.grindv2.api.annotations.Step;

/**
 * Invalid fixture: the first step consumes a type only the later step produces.
 */
public class ConsumedBeforeProducedSteps {

    @Step(order = 1)
    public void consume(Widget widget) {
    }

    @Step(order = 2)
    public Widget produce() {
        return new Widget("late");
    }

}
