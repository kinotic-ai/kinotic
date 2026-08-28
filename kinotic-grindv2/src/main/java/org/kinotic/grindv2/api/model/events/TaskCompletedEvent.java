package org.kinotic.grindv2.api.model.events;

import org.kinotic.grindv2.api.model.StoreType;

/**
 * A task finished successfully, describing what it stored in the job scope.
 *
 * @param taskPath    the task's position in the run's task tree
 * @param storeType   how the task stored its result, {@link StoreType#NONE} if it stored nothing
 * @param storedName  the name the result was stored under, or null if nothing was stored
 * @param storedValue the stored value, or null if nothing was stored. Carried for in-process
 *                    subscribers; remote watchers receive null
 */
public record TaskCompletedEvent(String taskPath,
                            StoreType storeType,
                            String storedName,
                            Object storedValue) implements JobRunEvent {
}
