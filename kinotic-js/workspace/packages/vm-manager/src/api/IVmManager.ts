import type { Workload } from '@kinotic-ai/management-api'

/**
 * Interface for managing VM workloads on a single node.
 * Delegates to the VM provider the node is configured to run.
 */
export interface IVmManager {

    /**
     * Starts a new workload on this node. For a detached workload the Promise resolves as
     * soon as the workload is running; for a non-detached one it resolves only once the run
     * has ended, with the final status and exit code.
     */
    startWorkload(workload: Workload): Promise<Workload>

    stopWorkload(workloadId: string): Promise<void>

    destroyWorkload(workloadId: string): Promise<void>

    getWorkload(workloadId: string): Promise<Workload>

    listWorkloads(): Promise<Workload[]>

}
