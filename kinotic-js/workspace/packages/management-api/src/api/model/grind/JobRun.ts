import type { Identifiable } from '@kinotic-ai/core'
import { ExecutionStatus } from '@/api/model/grind/ExecutionStatus'

/**
 * The persistent record of one execution of a grind job definition. The individual tasks
 * executed during the run are recorded as TaskRecords referencing this run's id.
 */
export class JobRun implements Identifiable<string> {

    /**
     * Unique identifier for this run.
     */
    public id: string | null = null

    /**
     * The name of the job definition this run executed.
     */
    public name: string = ''

    /**
     * The Organization this run executed on behalf of.
     * Null for platform runs (SYSTEM scope).
     */
    public organizationId: string | null = null

    /**
     * The Application this run executed on behalf of, or null if none.
     * When set, organizationId is also set.
     */
    public applicationId: string | null = null

    /**
     * The Project this run executed on behalf of, or null if none.
     * When set, organizationId is also set.
     */
    public projectId: string | null = null

    /**
     * The version of the job definition this run executed.
     */
    public version: string | null = null

    /**
     * The description of the executed job definition.
     */
    public description: string | null = null

    /**
     * Current status of the run.
     */
    public status: ExecutionStatus = ExecutionStatus.RUNNING

    /**
     * The failure message when status is FAILED.
     */
    public error: string | null = null

    /**
     * The id of the JobRun this run resumed, or null if this run was not a resume.
     */
    public resumedFrom: string | null = null

    /**
     * When the run started executing, as epoch milliseconds.
     */
    public started: number | null = null

    /**
     * When the run reached a terminal status, as epoch milliseconds.
     */
    public finished: number | null = null

}
