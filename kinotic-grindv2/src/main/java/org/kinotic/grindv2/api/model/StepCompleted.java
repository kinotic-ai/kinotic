package org.kinotic.grindv2.api.model;

/**
 * A step finished successfully, describing what it stored in the job scope.
 *
 * @param stepPath    the step's position in the run's step tree
 * @param storeType   how the step stored its result, {@link StoreType#NONE} if it stored nothing
 * @param storedName  the name the result was stored under, or null if nothing was stored
 * @param storedValue the stored value, or null if nothing was stored. Carried for in-process
 *                    subscribers; remote watchers receive null
 */
public record StepCompleted(String stepPath,
                            StoreType storeType,
                            String storedName,
                            Object storedValue) implements JobRunEvent {
}
