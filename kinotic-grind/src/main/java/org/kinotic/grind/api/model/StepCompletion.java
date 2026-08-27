package org.kinotic.grind.api.model;

/**
 * The value of a {@link ResultType#STEP_COMPLETED} result, describing what the completed step
 * stored in the {@code JobContext} when it finished.
 *
 * @param storeType   How the step stored its result, {@link StoreType#NONE} if it stored nothing.
 * @param storedName  The name the step's result was stored under, or null if the step stored nothing
 *                    or stored under generated names.
 * @param storedValue The value the step stored in the {@code JobContext}, or null if the step stored nothing.
 */
public record StepCompletion(StoreType storeType, String storedName, Object storedValue) {

}
