package org.kinotic.orchestrator.api.grind;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The value of a {@link ResultType#STEP_COMPLETED} result, describing what the {@link Step}
 * stored in the {@link JobContext} when it finished.
 */
@Getter
@RequiredArgsConstructor
public class StepCompletion {

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
