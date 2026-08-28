package org.kinotic.grindv2.internal.model;

/**
 * Signals that the run's thread was interrupted by cancellation. The interpreter converts
 * interruption into this exception so cancellation unwinds without recording task failures.
 */
public class RunCancelledException extends RuntimeException {

    public RunCancelledException() {
        super("The run was cancelled");
    }

}
