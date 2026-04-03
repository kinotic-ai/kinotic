import type { Workload } from '@kinotic-ai/os-api'

/**
 * Interface for managing VM workloads on a single node.
 * Delegates to the appropriate VM provider (boxlite, firecracker, etc.) based on the workload configuration.
 */
export interface IVmManager {

    startWorkload(workload: Workload): Promise<Workload>

    stopWorkload(workloadId: string): Promise<void>

    destroyWorkload(workloadId: string): Promise<void>

    getWorkload(workloadId: string): Promise<Workload>

    listWorkloads(): Promise<Workload[]>

}
