package org.kinotic.grind.internal.model;

import org.kinotic.grind.api.model.StoreType;
import tools.jackson.databind.JsonNode;

/**
 * What a resumed run knows about one completed task of the original run.
 *
 * @param storeType    how the completed task stored its result
 * @param dynamicTasks true if the completed task produced dynamic tasks, meaning it must be
 *                     re-executed so those tasks are regenerated
 * @param value        the recorded stored value, deserialized, or null if the task stored
 *                     nothing or the value could not be restored
 * @param view         the view the completed task's record kept, or null when it kept none
 */
public record ReplayEntry(StoreType storeType, boolean dynamicTasks, Object value, JsonNode view) {
}
