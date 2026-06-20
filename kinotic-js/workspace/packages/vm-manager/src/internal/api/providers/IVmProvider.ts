import type { Workload } from '@kinotic-ai/os-api'

/**
 * Abstraction for a VM provider that can manage micro VM lifecycle.
 * Implementations handle the specifics of each hypervisor (boxlite, firecracker, cloud-hypervisor, etc.)
 */
export interface IVmProvider {

    /**
     * Starts a new VM for the given workload.
     * @param workload the workload configuration
     * @return a Promise resolving to the workload with updated status
     */
    start(workload: Workload): Promise<Workload>

    /**
     * Stops a running VM for the given workload.
     * @param workloadId the id of the workload to stop
     */
    stop(workloadId: string): Promise<void>

    /**
     * Destroys a VM for the given workload, removing all resources.
     * @param workloadId the id of the workload to destroy
     */
    destroy(workloadId: string): Promise<void>

    /**
     * Gets the current status/state of a workload's VM.
     * @param workloadId the id of the workload
     * @return a Promise resolving to the updated workload
     */
    getWorkload(workloadId: string): Promise<Workload>

    /**
     * Lists all VMs managed by this provider.
     * @return a Promise resolving to an array of workloads
     */
    listWorkloads(): Promise<Workload[]>
}
