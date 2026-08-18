import type { Workload } from '@kinotic-ai/os-api'

/**
 * Interface for managing VM workloads on a single node.
 * Delegates to the VM provider the node is configured to run.
 */
export interface IVmManager {

    startWorkload(workload: Workload): Promise<Workload>

    /**
     * Restarts a stopped workload in place: the same VM boots again with its disk state
     * intact and the workload's entrypoint runs again. Fails unless the workload is
     * STOPPED and its VM still exists (a workload stopped with autoRemove has none).
     */
    restartWorkload(workloadId: string): Promise<Workload>

    stopWorkload(workloadId: string): Promise<void>

    destroyWorkload(workloadId: string): Promise<void>

    getWorkload(workloadId: string): Promise<Workload>

    listWorkloads(): Promise<Workload[]>

}
