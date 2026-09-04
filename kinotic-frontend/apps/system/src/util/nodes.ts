import { Direction, Kinotic, Order, Pageable, Sort } from '@kinotic-ai/core'
import { VmNodeStatusType, type VmNode } from '@kinotic-ai/system-api'

/** How many worker nodes the console reads; a platform with more shows the first page of them. */
const NODE_PAGE_SIZE = 100

// Nodes that can actually take a workload belong at the top. status.type is a keyword, and its
// three values sort ONLINE, OFFLINE, DRAINING descending, so descending is the order the pages
// want; name breaks ties so cards hold a stable position across refreshes.
const NODE_SORT = new Sort()
NODE_SORT.orders = [new Order('status.type', Direction.DESC), new Order('name', Direction.ASC)]

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
    return page.content ?? []
}

/** Maps a node status to the PrimeVue Tag severity it renders with. */
export function nodeSeverity(status: VmNodeStatusType): string {
    let ret: string
    if (status === VmNodeStatusType.ONLINE) {
        ret = 'success'
    } else if (status === VmNodeStatusType.DRAINING) {
        ret = 'warn'
    } else {
        ret = 'danger'
    }
    return ret
}

export function percentOf(part: number, total: number): number {
    return total > 0 ? Math.round((part / total) * 100) : 0
}

/** The capacity of the nodes added up: what they promised at registration, less what is placed on them. */
export function capacityOf(nodes: VmNode[]): Capacity {
    const ret: Capacity = { cpus: 0, memoryMb: 0, diskMb: 0, usedCpus: 0, usedMemoryMb: 0, usedDiskMb: 0 }
    for (const node of nodes) {
        ret.cpus += node.totalCpus
        ret.memoryMb += node.totalMemoryMb
        ret.diskMb += node.totalDiskMb
        ret.usedCpus += node.totalCpus - node.availableCpus
        ret.usedMemoryMb += node.totalMemoryMb - node.availableMemoryMb
        ret.usedDiskMb += node.totalDiskMb - node.availableDiskMb
    }
    return ret
}
