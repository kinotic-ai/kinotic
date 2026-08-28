package org.kinotic.grindv2;

import org.kinotic.grindv2.api.annotations.Task;
import org.kinotic.grindv2.api.model.StoreType;

/**
 * Tasks class publishing a wire-only value: kept nowhere, visible to watchers.
 */
public class WireTasks {

    @Task(order = 1, value = "allocate widget", store = StoreType.NONE, wire = true)
    public Widget allocate() {
        return new Widget("observed");
    }

}
