

package org.kinotic.orchestrator.api;

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
     * Result contains new {@link Step}'s that have been returned by a {@link Task} execution
     * This is used to update the known {@link Step}'s when wanting to receive progress notifications
     * The result value will contain the new {@link Step}
     */
    DYNAMIC_STEPS,
    /**
     * The result value is a {@link Throwable} indicating that an error occurred at the given step
     */
    EXCEPTION
}
