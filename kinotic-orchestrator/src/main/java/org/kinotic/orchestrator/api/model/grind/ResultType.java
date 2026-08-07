

package org.kinotic.orchestrator.api.model.grind;

/**
 *
 * Created by Navid Mitchell on 11/11/20
 */
public enum ResultType {
    /**
     * The result value is the final VALUE of the task
     */
    VALUE,
    /**
     * The task resulted in no action being taken the value will be null
     */
    NOOP,
    /**
     * The result value is a Diagnostic message
     */
    DIAGNOSTIC,
    /**
     * The result value is a {@link Progress} object
     */
    PROGRESS,
    /**
     * Result contains new dynamically created steps that have been returned by a {@link Task} execution
     * This is used to update the known steps when wanting to receive progress notifications
     * The result value will contain the new step
     */
    DYNAMIC_STEPS,
    /**
     * The result value is a {@link Throwable} indicating that an error occurred at the given step
     */
    EXCEPTION,
    /**
     * The step began executing, the result value is the step description.
     * Always emitted regardless of the {@link ResultOptions} used
     */
    STEP_STARTED,
    /**
     * The step finished successfully, the result value is a {@link StepCompletion}.
     * Always emitted regardless of the {@link ResultOptions} used
     */
    STEP_COMPLETED,
    /**
     * The step terminated with a failure, the result value is the {@link Throwable}.
     * Always emitted regardless of the {@link ResultOptions} used
     */
    STEP_FAILED
}
