import type { WorkloadStatus } from '@kinotic-ai/management-api'

/**
 * A vm-manager node's view of one workload's status, reported to the orchestrator.
 */
export interface WorkloadStatusReport {

    /** Id of the workload the report is about. */
    workloadId: string

    /** The workload's status on the node. */
    status: WorkloadStatus

    /** The exit status of the workload's process once its run has ended, null while it has not. */
    exitCode: number | null

    /** When the node recorded this status, in epoch milliseconds. */
    updated: number
}
