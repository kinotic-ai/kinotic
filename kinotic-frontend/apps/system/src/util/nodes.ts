import { Direction, Kinotic, Order, Pageable, Sort, StatusConditionType, findStatusCondition,
         type StatusCondition } from '@kinotic-ai/core'
import { VmNodeStatusType, type VmNode } from '@kinotic-ai/system-api'

/** How many worker nodes the console reads; a platform with more shows the first page of them. */
const NODE_PAGE_SIZE = 100

/**
 * What the console shows for a node: what it last reported, unless the orchestrator cannot reach it,
 * or nothing has been reported yet.
 */
export enum NodeHealth {
    ONLINE = 'ONLINE',
    DRAINING = 'DRAINING',
    UNREACHABLE = 'UNREACHABLE',
    UNKNOWN = 'UNKNOWN'
}

// Nodes that can actually take a workload belong at the top, then the ones the orchestrator is
// holding off, then the ones it cannot reach; name breaks ties so cards hold a stable position
// across refreshes.
const NODE_RANK: Record<NodeHealth, number> = {
    [NodeHealth.ONLINE]: 0,
    [NodeHealth.DRAINING]: 1,
    [NodeHealth.UNREACHABLE]: 2,
    [NodeHealth.UNKNOWN]: 3
}
const NODE_SORT = new Sort()
NODE_SORT.orders = [new Order('name', Direction.ASC)]

/** The orchestrator's mark that the node has not answered, or undefined while it answers. */
export function nodeUnreachable(node: VmNode): StatusCondition | undefined {
    return findStatusCondition(node.state.conditions, StatusConditionType.NODE_UNREACHABLE)
}

/** The node's health as the console shows it: the mark outranks what the node last reported. */
export function nodeHealth(node: VmNode): NodeHealth {
    let ret: NodeHealth
    if (nodeUnreachable(node)) {
        ret = NodeHealth.UNREACHABLE
    } else if (node.state.observed?.phase === VmNodeStatusType.DRAINING) {
        ret = NodeHealth.DRAINING
    } else if (node.state.observed?.phase === VmNodeStatusType.ONLINE) {
        ret = NodeHealth.ONLINE
    } else {
        ret = NodeHealth.UNKNOWN
    }
    return ret
}

/** What a node, or a set of nodes, promised and what is placed on it. */
export interface Capacity {
    cpus: number
    memoryMb: number
    diskMb: number
    usedCpus: number
    usedMemoryMb: number
    usedDiskMb: number
}

/** Every registered worker node, the ones fit for placement first. */
export async function loadNodes(): Promise<VmNode[]> {
    const page = await Kinotic.vmNodes.findAll(Pageable.create(0, NODE_PAGE_SIZE, NODE_SORT))
    return (page.content ?? []).sort((a, b) => NODE_RANK[nodeHealth(a)] - NODE_RANK[nodeHealth(b)])
}

/** Maps a node's health to the PrimeVue Tag severity it renders with. */
export function nodeSeverity(health: NodeHealth): string {
    let ret: string
    if (health === NodeHealth.ONLINE) {
        ret = 'success'
    } else if (health === NodeHealth.DRAINING) {
        ret = 'warn'
    } else if (health === NodeHealth.UNREACHABLE) {
        ret = 'danger'
    } else {
        ret = 'secondary'
    }
    return ret
}

export function percentOf(part: number, total: number): number {
    return total > 0 ? Math.round((part / total) * 100) : 0
}

/** A CPU allotment in cores, with a fraction shown to the hundredth and no trailing zeros: 4, 0.5, 2.25. */
export function formatCpus(cpus: number): string {
    return String(Math.round(cpus * 100) / 100)
}

/** The capacity of the nodes added up: what they promised at registration, less what is placed on them. */
export function capacityOf(nodes: VmNode[]): Capacity {
    const ret: Capacity = { cpus: 0, memoryMb: 0, diskMb: 0, usedCpus: 0, usedMemoryMb: 0, usedDiskMb: 0 }
    for (const node of nodes) {
        ret.cpus += node.totalCpus
        ret.memoryMb += node.totalMemoryMb
        ret.diskMb += node.totalDiskMb
        ret.usedCpus += node.totalCpus - node.freeCpus
        ret.usedMemoryMb += node.totalMemoryMb - node.freeMemoryMb
        ret.usedDiskMb += node.totalDiskMb - node.freeDiskMb
    }
    return ret
}
