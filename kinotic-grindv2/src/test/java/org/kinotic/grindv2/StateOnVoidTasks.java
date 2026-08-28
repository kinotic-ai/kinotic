package org.kinotic.grindv2;

import org.kinotic.grindv2.api.annotations.Task;
import org.kinotic.grindv2.api.model.StoreType;

/**
 * Invalid fixture: durable state declared on a task that produces no value.
 */
public class StateOnVoidTasks {

    @Task(order = 1, store = StoreType.STATE)
    public void nothing() {
    }

}
