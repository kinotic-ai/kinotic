package org.kinotic.orchestrator.api.model.grind;

/**
 * The lifecycle state of a recorded execution, shared by {@link JobRun}s and {@link TaskRecord}s.
 */
public enum ExecutionStatus {

    /**
     * The execution has been discovered but has not started. Only {@link TaskRecord}s carry
     * this status: a step is recorded PENDING the moment it becomes known - at run start for
     * the definition's static steps, at discovery for dynamic ones. A record that keeps this
     * status after its {@link JobRun} reached a terminal status indicates the step was never
     * reached.
     */
    PENDING,

    /**
     * The execution is currently in progress. A {@link TaskRecord} that keeps this status
     * after its {@link JobRun} reached a terminal status indicates the step never finished.
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
