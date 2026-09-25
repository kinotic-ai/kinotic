import { ReconcileState, type Reconcilable } from '@kinotic-ai/core'
import type { VmNodeState } from '@/api/model/workload/VmNodeState'
import type { WorkloadReservation } from '@/api/model/workload/WorkloadReservation'
import { VmProviderType } from '@/api/model/workload/VmProviderType'

/**
 * Represents a node in the cluster that is running a VmManager process
 * and is capable of hosting workloads.
 */
export class VmNode implements Reconcilable<VmNodeState> {

    /**
     * Unique identifier for this node.
     */
    public id: string

    /**
     * Human-readable name for the node.
     */
    public name: string

    /**
     * The hostname or address of the node.
     */
    public hostname: string

    /**
     * What the node should be, taking workloads, and what it reports it is, with what the platform
     * inferred beside the node's word: that it fell silent, or that a call to it could not be
     * delivered. A node is placeable exactly when this is reconciled.
     */
    public state: ReconcileState<VmNodeState> = new ReconcileState()

    /**
     * Why the node is not taking workloads, or null when it is. Set from the node's own report of
     * the guarantees it can still make — a data root that stopped enforcing disk limits, or a
     * firewall that stopped hiding host credentials from guests.
     */
    public healthMessage: string | null = null

    /**
     * The VM provider this node runs every workload on, determined by how the node was
     * provisioned and reported when it registers.
     */
    public providerType: VmProviderType = VmProviderType.BOXLITE

    /**
     * Total number of vCPUs available on this node.
     */
    public totalCpus: number = 0

    /**
     * Total memory available on this node in megabytes.
     */
    public totalMemoryMb: number = 0

    /**
     * Total disk space available on this node in megabytes.
     */
    public totalDiskMb: number = 0

    /**
     * CPU not allocated to any workload, in cores. What is allocated is
     * totalCpus - freeCpus, the sum of the reservations.
     */
    public freeCpus: number = 0

    /**
     * Memory not allocated to any workload, in megabytes.
     */
    public freeMemoryMb: number = 0

    /**
     * Disk space not allocated to any workload, in megabytes.
     */
    public freeDiskMb: number = 0

    /**
     * The room each workload running on this node holds, one entry per workload. The free*
     * fields are the totals less what these hold, so a workload's room is reserved and released
     * by its id and never counted twice.
     */
    public reservations: WorkloadReservation[] = []

    /**
     * The date and time the node was last seen/heartbeat.
     */
    public lastSeen: number | null = null

    /**
     * Base directory every workload volume mount on this node must live under. Reported by
     * the node at registration; deployment flows compose host paths under it.
     */
    public workloadDataDir: string | null = null

    constructor(id: string, name: string, hostname: string) {
        this.id = id
        this.name = name
        this.hostname = hostname
    }
}
