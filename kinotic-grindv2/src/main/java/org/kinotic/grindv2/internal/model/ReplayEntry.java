package org.kinotic.grindv2.internal.model;

import org.kinotic.grindv2.api.model.StoreType;

/**
 * What a resumed run knows about one completed step of the original run.
 *
 * @param storeType    how the completed step stored its result
 * @param dynamicSteps true if the completed step produced dynamic steps, meaning it must be
 *                     re-executed so those steps are regenerated
 * @param value        the recorded stored value, deserialized, or null if the step stored
 *                     nothing or the value could not be restored
 */
public record ReplayEntry(StoreType storeType, boolean dynamicSteps, Object value) {
}
