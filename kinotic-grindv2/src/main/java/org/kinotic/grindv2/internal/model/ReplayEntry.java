package org.kinotic.grindv2.internal.model;

import org.kinotic.grindv2.api.model.StoreType;

/**
 * What a resumed run knows about one completed task of the original run.
 *
 * @param storeType    how the completed task stored its result
 * @param dynamicTasks true if the completed task produced dynamic tasks, meaning it must be
 *                     re-executed so those tasks are regenerated
 * @param value        the recorded stored value, deserialized, or null if the task stored
 *                     nothing or the value could not be restored
 */
public record ReplayEntry(StoreType storeType, boolean dynamicTasks, Object value) {
}
