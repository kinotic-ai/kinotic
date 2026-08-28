package org.kinotic.grindv2;

import org.kinotic.grindv2.api.annotations.Step;

/**
 * Invalid fixture: two steps claim the same order.
 */
public class DuplicateOrderSteps {

    @Step(order = 1)
    public void first() {
    }

    @Step(order = 1)
    public void second() {
    }

}
