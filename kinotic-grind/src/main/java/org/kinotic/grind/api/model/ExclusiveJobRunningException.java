package org.kinotic.grind.api.model;

/**
 * Fails the events of a run or resume of an {@link JobDefinition#exclusive() exclusive} job that
 * was refused because another run of the job's name is executing for the same owner.
 */
public class ExclusiveJobRunningException extends IllegalStateException {

    /**
     * @param jobName the name of the exclusive job
     * @param cause   why the run could not take the job's turn
     */
    public ExclusiveJobRunningException(String jobName, Throwable cause) {
        super("Exclusive job " + jobName + " is already running for its owner", cause);
    }

}
