/**
 * The lifecycle state of a recorded execution, shared by JobRuns and StepRecords.
 */
export enum ExecutionStatus {
    /**
     * The execution has been discovered but has not started. Only StepRecords carry this
     * status: a step is recorded PENDING the moment it becomes known. A record that keeps
     * this status after its JobRun reached a terminal status was never reached.
     */
    PENDING = 'PENDING',
    /**
     * The execution is currently in progress. A StepRecord that keeps this status after its
     * JobRun reached a terminal status indicates the step never finished.
     */
    RUNNING = 'RUNNING',
    /**
     * The execution finished successfully.
     */
    COMPLETED = 'COMPLETED',
    /**
     * The execution terminated with a failure.
     */
    FAILED = 'FAILED',
    /**
     * The execution was cancelled before it finished.
     */
    CANCELLED = 'CANCELLED'
}
