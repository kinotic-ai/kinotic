package org.kinotic.grindv2.api;

/**
 * The lifecycle state of a recorded execution, shared by {@link JobRun}s and {@link StepRecord}s.
 */
public enum ExecutionStatus {

    /**
     * The execution has been discovered but has not started. Only {@link StepRecord}s carry
     * this status: a step is recorded PENDING the moment it becomes known - at run start for
     * the definition's static steps, at discovery for dynamic ones.
     */
    PENDING,

    /**
     * The execution is currently in progress.
     */
    RUNNING,

    /**
     * The execution finished successfully.
     */
    COMPLETED,

    /**
     * The execution terminated with a failure.
     */
    FAILED,

    /**
     * The execution was cancelled before it finished.
     */
    CANCELLED

}
