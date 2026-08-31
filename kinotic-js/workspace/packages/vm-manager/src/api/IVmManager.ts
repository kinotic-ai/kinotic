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

    /**
     * Restarts a stopped workload in place: the same VM boots again with its disk state
     * intact and the workload's entrypoint runs again. Fails unless the workload is
     * STOPPED and its VM still exists (a workload stopped with autoRemove has none).
     * Resolves at boot or at run end the same way as {@link startWorkload}.
     */
    restartWorkload(workloadId: string): Promise<Workload>

    stopWorkload(workloadId: string): Promise<void>

    destroyWorkload(workloadId: string): Promise<void>

    getWorkload(workloadId: string): Promise<Workload>

    listWorkloads(): Promise<Workload[]>

}
