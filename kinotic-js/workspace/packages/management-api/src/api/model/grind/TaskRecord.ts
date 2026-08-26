import type { Identifiable } from '@kinotic-ai/core'
import { RunStatus } from '@/api/model/grind/RunStatus'
import { StoreType } from '@/api/model/grind/StoreType'

/**
 * The persistent record of one step within a JobRun. Every discovered step has a record,
 * PENDING until it starts executing, so a run's records are its complete step ledger.
 */
export class TaskRecord implements Identifiable<string> {

    /**
     * Unique identifier for this record, composed of the jobRunId and the stepPath,
     * so a step has exactly one record per run.
     */
    public id: string | null = null

    /**
     * The id of the JobRun this record belongs to.
     */
    public jobRunId: string = ''

    /**
     * The position of the step within the run's step tree, as the `/` separated sequence
     * numbers from the root job definition down to the step.
     */
    public stepPath: string = ''

    /**
     * The description of the executed task or job definition.
     */
    public description: string | null = null

    /**
     * Current status of the step.
     */
    public status: RunStatus = RunStatus.RUNNING

    /**
     * How the step's result was stored in the job scope.
     * Null until the step completes.
     */
    public storeType: StoreType | null = null

    /**
     * True if the step's task returned further steps that were executed dynamically.
     */
    public dynamicSteps: boolean = false

    /**
     * The name the step's result was stored under in the job scope,
     * or null if the result was not stored.
     */
    public resultName: string | null = null

    /**
     * The Java type of the stored result. Only set when storeType is STATE.
     */
    public resultValueType: string | null = null

    /**
     * The stored result as JSON. Only set when storeType is STATE.
     */
    public resultValue: any = null

    /**
     * Output produced by the step while executing, such as command output.
     */
    public output: string | null = null

    /**
     * The failure message when status is FAILED.
     */
    public error: string | null = null

    /**
     * When the step started executing, as epoch milliseconds.
     */
    public started: number | null = null

    /**
     * When the step reached a terminal status, as epoch milliseconds.
     */
    public finished: number | null = null

}
