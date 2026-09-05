import type { VmProviderType } from '@kinotic-ai/system-api'
import type { Workload } from '@kinotic-ai/management-api'
import type { TelemetryTarget } from '@/internal/api/model/TelemetryTarget'

/**
 * Abstraction for a VM provider that can manage micro VM lifecycle.
 * Implementations handle the specifics of each hypervisor (boxlite, cloud-hypervisor, etc.)
 */
export interface IVmProvider {

    /**
     * Identifies this implementation to the orchestrator. A node runs exactly one provider,
     * chosen by its own configuration, and reports it when it registers.
     */
    readonly type: VmProviderType

    /**
     * Capacity of the filesystem this provider gives workloads their disks from, in
     * megabytes. This is what the orchestrator schedules {@link Workload#diskSizeMb}
     * against, and each provider keeps its guest disks somewhere different.
     */
    totalDiskMb(): Promise<number>

    /**
     * What this provider can no longer guarantee about the node, empty when it is fit to
     * receive workloads. Called on every heartbeat, because the things a provider depends on
     * — a data root that enforces disk limits, a firewall that hides host credentials from
     * guests — can stop being true while it runs, and each fails silently.
     */
    checkNodeHealth(): Promise<string[]>

    /**
     * Restores the workload state persisted by a previous vm-manager process on this node,
     * reattaching to VMs that are still running.
     */
    recover(): Promise<void>

    /**
     * Starts a new VM for the given workload, resolving as soon as the VM is running. A
     * non-detached workload's outcome is observed separately through {@link awaitExit}.
     * @param workload the workload configuration
     * @return a Promise resolving to the workload with updated status
     */
    start(workload: Workload): Promise<Workload>

    /**
     * Restarts a stopped workload in place: the same VM boots again with its disk state
     * intact and the workload's entrypoint runs again. Fails unless the workload is
     * STOPPED and its VM still exists (a workload stopped with autoRemove has none).
     * Resolves at boot the same way as {@link start}.
     * @param workloadId the id of the workload to restart
     * @return a Promise resolving to the workload with updated status
     */
    restart(workloadId: string): Promise<Workload>

    /**
     * Waits for a running workload's run to end, resolving with its final status and exit
     * code, or rejecting if the provider cannot observe the guest's exit.
     * @param workloadId the id of the workload to wait for
     * @return a Promise resolving to the workload once its run has ended
     */
    awaitExit(workloadId: string): Promise<Workload>

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
     * Lists the telemetry sources of this provider's VMs. A VM whose run has ended keeps its
     * sources until {@link destroy}, so what it wrote after the shipper last read it still ships.
     * @return a Promise resolving to one target per VM with a log source on this node
     */
    listTelemetryTargets(): Promise<TelemetryTarget[]>
}
