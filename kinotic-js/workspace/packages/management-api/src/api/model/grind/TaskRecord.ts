import type { Identifiable } from '@kinotic-ai/core'
import { ExecutionStatus } from '@/api/model/grind/ExecutionStatus'
import { StoreType } from '@/api/model/grind/StoreType'

/**
 * The persistent record of one task within a JobRun. Every discovered task has a record,
 * PENDING until it starts executing, so a run's records are its complete task ledger.
 */
export class TaskRecord implements Identifiable<string> {

    /**
     * Unique identifier for this record, composed of the jobRunId and the taskPath,
     * so a task has exactly one record per run.
     */
    public id: string | null = null

    /**
     * The id of the JobRun this record belongs to.
     */
    public jobRunId: string = ''

    /**
     * The position of the task within the run's task tree, as the `/` separated sequence
     * numbers from the root job definition down to the task.
     */
    public taskPath: string = ''

    /**
     * The description of the executed task or job definition.
     */
    public description: string | null = null

    /**
     * Current status of the task.
     */
    public status: ExecutionStatus = ExecutionStatus.RUNNING

    /**
     * How the task's result was stored in the job scope.
     * Null until the task completes.
     */
    public storeType: StoreType | null = null

    /**
     * True if the task produced further tasks that were executed dynamically.
     */
    public dynamicTasks: boolean = false

    /**
     * The name the task's value was stored under in the job scope,
     * or null if nothing was stored.
     */
    public storedName: string | null = null

    /**
     * The Java type of the durable state. Only set when storeType is STATE.
     */
    public stateValueType: string | null = null

    /**
     * The durable state as JSON, replayed when the run is resumed. Only set when storeType
     * is STATE.
     */
    public stateValue: any = null

    /**
     * The failure message when status is FAILED.
     */
    public error: string | null = null

    /**
     * When the task started executing, as epoch milliseconds.
     */
    public started: number | null = null

    /**
     * When the task reached a terminal status, as epoch milliseconds.
     */
    public finished: number | null = null

}
