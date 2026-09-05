package org.kinotic.grind;

import org.kinotic.grind.api.annotations.Task;
import org.kinotic.grind.api.model.StoreType;

/**
 * Invalid fixture: durable state declared on a task that produces no value.
 */
public class StateOnVoidTasks {

    @Task(order = 1, store = StoreType.STATE)
    public void nothing() {
    }

}
