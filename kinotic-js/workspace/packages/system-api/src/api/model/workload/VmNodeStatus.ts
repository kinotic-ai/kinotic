import { VmNodeStatusType } from '@/api/model/workload/VmNodeStatusType'

/**
 * Whether a node is fit to receive workloads, and why when it is not.
 */
export class VmNodeStatus {

    /**
     * Whether the node is taking workloads.
     */
    public type: VmNodeStatusType = VmNodeStatusType.ONLINE

    /**
     * Why the node is not taking workloads, or null when it is. Set from the node's own report
     * of the guarantees it can still make — a data root that stopped enforcing disk limits, or
     * a firewall that stopped hiding host credentials from guests.
     */
    public healthMessage: string | null = null

}
