package org.kinotic.grindv2;

import org.kinotic.grindv2.api.annotations.Step;
import org.kinotic.grindv2.api.model.StoreType;

/**
 * Invalid fixture: durable state declared on a step that produces no value.
 */
public class StateOnVoidSteps {

    @Step(order = 1, store = StoreType.STATE)
    public void nothing() {
    }

}
