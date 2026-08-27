package org.kinotic.grindv2;

import org.kinotic.grindv2.api.Step;
import org.kinotic.grindv2.api.StoreType;

/**
 * Invalid fixture: durable state declared on a step that produces no value.
 */
public class StateOnVoidSteps {

    @Step(order = 1, store = StoreType.STATE)
    public void nothing() {
    }

}
