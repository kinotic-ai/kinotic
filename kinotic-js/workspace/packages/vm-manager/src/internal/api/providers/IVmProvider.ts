import type { Workload, VmProviderType } from '@kinotic-ai/os-api'
import type { LogTarget } from '@/model/LogTarget'

/**
 * Abstraction for a VM provider that can manage micro VM lifecycle.
 * Implementations handle the specifics of each hypervisor (boxlite, firecracker, cloud-hypervisor, etc.)
 */
export interface IVmProvider {

    /**
     * The provider type workloads select via {@link Workload#providerType}.
     */
    readonly type: VmProviderType

    /**
     * Restores the workload state persisted by a previous vm-manager process on this node,
     * reattaching to VMs that are still running.
     */
    recover(): Promise<void>

    /**
     * Starts a new VM for the given workload.
     * @param workload the workload configuration
     * @return a Promise resolving to the workload with updated status
     */
    start(workload: Workload): Promise<Workload>

    /**
     * Restarts a stopped workload in place: the same VM boots again with its disk state
     * intact and the workload's entrypoint runs again. Fails unless the workload is
     * STOPPED and its VM still exists (a workload stopped with autoRemove has none).
     * @param workloadId the id of the workload to restart
     * @return a Promise resolving to the workload with updated status
     */
    restart(workloadId: string): Promise<Workload>

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

    /**
     * Lists the log sources of this provider's running VMs.
     * @return a Promise resolving to one target per running VM
     */
    listLogTargets(): Promise<LogTarget[]>
}
