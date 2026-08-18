import type { Identifiable } from '@kinotic-ai/core'
import { VmNodeStatus } from '@/api/model/workload/VmNodeStatus'
import { VmProviderType } from '@/api/model/workload/VmProviderType'

/**
 * Represents a node in the cluster that is running a VmManager process
 * and is capable of hosting workloads.
 */
export class VmNode implements Identifiable<string> {

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
     * Current status of the node.
     */
    public status: VmNodeStatus = VmNodeStatus.ONLINE

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
     * Number of vCPUs currently allocated to workloads.
     */
    public allocatedCpus: number = 0

    /**
     * Memory currently allocated to workloads in megabytes.
     */
    public allocatedMemoryMb: number = 0

    /**
     * Disk space currently allocated to workloads in megabytes.
     */
    public allocatedDiskMb: number = 0

    /**
     * Why the node is not fit to receive workloads, or null when it is. Set from the node's
     * own report of the guarantees it can still make — a data root that stopped enforcing
     * disk limits, or a firewall that stopped hiding host credentials from guests.
     */
    public healthMessage: string | null = null

    /**
     * The date and time the node was last seen/heartbeat.
     */
    public lastSeen: number | null = null

    constructor(id: string, name: string, hostname: string) {
        this.id = id
        this.name = name
        this.hostname = hostname
    }
}
