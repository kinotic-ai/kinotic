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
     * Whether the node is fit to receive workloads, and why when it is not.
     */
    public status: VmNodeStatus = new VmNodeStatus()

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
     * Number of vCPUs not allocated to any workload. What is allocated is
     * totalCpus - availableCpus.
     */
    public availableCpus: number = 0

    /**
     * Memory not allocated to any workload, in megabytes.
     */
    public availableMemoryMb: number = 0

    /**
     * Disk space not allocated to any workload, in megabytes.
     */
    public availableDiskMb: number = 0

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
