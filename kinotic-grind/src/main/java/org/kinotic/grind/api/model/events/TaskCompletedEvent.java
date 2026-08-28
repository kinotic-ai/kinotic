package org.kinotic.grind.api.model.events;

import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.StoreType;
import tools.jackson.databind.JsonNode;

/**
 * A task finished successfully, describing what it stored in the job scope.
 *
 * @param taskPath    the task's position in the run's task tree
 * @param storeType   how the task stored its result, {@link StoreType#NONE} if it stored nothing
 * @param storedName  the name the result was stored under, or null if nothing was stored
 * @param storedValue the stored value, or null if nothing was stored. Carried for in-process
 *                    subscribers; remote watchers receive null
 * @param wireValue   the stored value serialized as JSON, or null unless the task's
 *                    {@link Store} declared wire publication. Safe to cross a serialization
 *                    boundary, so remote watchers receive it
 */
public record TaskCompletedEvent(String taskPath,
                                 StoreType storeType,
                                 String storedName,
                                 Object storedValue,
                                 JsonNode wireValue) implements JobRunEvent {
}
