package org.kinotic.system.api.model.grind;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The value of a {@link ResultType#STEP_COMPLETED} result, describing what the completed step
 * stored in the {@link JobContext} when it finished.
 */
@Getter
@RequiredArgsConstructor
public class StepCompletion {

    /**
     * How the step stored its result, {@link StoreType#NONE} if it stored nothing.
     */
    private final StoreType storeType;

    /**
     * The name the step's result was stored under, or null if the step stored nothing
     * or stored under generated names.
     */
    private final String storedName;

    /**
     * The value the step stored in the {@link JobContext}, or null if the step stored nothing.
     */
    private final Object storedValue;

}
