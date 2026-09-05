package org.kinotic.grind;

import org.kinotic.grind.api.annotations.Task;
import org.kinotic.grind.api.model.StoreType;

/**
 * Tasks class publishing a wire-only value: kept nowhere, visible to watchers.
 */
public class WireTasks {

    @Task(order = 1, value = "allocate widget", store = StoreType.NONE, wire = true)
    public Widget allocate() {
        return new Widget("observed");
    }

}
