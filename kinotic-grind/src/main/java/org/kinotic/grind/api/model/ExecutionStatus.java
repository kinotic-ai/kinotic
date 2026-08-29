package org.kinotic.grind.api.model;

/**
 * The lifecycle state of a recorded execution, shared by {@link JobRun}s and {@link TaskRecord}s.
 */
public enum ExecutionStatus {

    /**
     * The execution has been discovered but has not started. Only {@link TaskRecord}s carry
     * this status: a task is recorded PENDING the moment it becomes known - at run start for
     * the definition's static tasks, at discovery for dynamic ones.
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
