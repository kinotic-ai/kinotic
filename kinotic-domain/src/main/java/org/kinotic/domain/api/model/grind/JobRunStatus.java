package org.kinotic.domain.api.model.grind;

/**
 * The lifecycle state of a {@link JobRun}.
 */
public enum JobRunStatus {

    /**
     * The run is currently executing.
     */
    RUNNING,

    /**
     * The run finished with every step completing successfully.
     */
    COMPLETED,

    /**
     * The run terminated because a step failed.
     */
    FAILED,

    /**
     * The run was cancelled before it finished.
     */
    CANCELLED

}
