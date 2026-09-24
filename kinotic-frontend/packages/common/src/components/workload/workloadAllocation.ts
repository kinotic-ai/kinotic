import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import { formatCpus, formatMb } from '../../util/helpers'

/** What the workloads of one owner hold of the nodes they run on. */
export interface Allocation {
    /** The owner the workloads run for, as the grouping named it. */
    owner: string
    workloads: number
    cpus: number
    memoryMb: number
    diskMb: number
}

/**
 * Names the organization's own workloads where workloads are grouped by application. Ids are
 * lowercase letters, digits and dashes, so no application can take it.
 */
export const ORGANIZATION_OWNER = 'the organization'

/** The application a workload runs for, as a grouping by application names it. */
export function applicationOwnerOf(workload: Workload): string {
    return workload.applicationId ?? ORGANIZATION_OWNER
}

// A run holds its room on the node from placement until it ends, which is what the node's
// capacity ledger reserves and releases
const HOLDING_STATES = new Set([WorkloadStatus.STARTING, WorkloadStatus.RUNNING, WorkloadStatus.STOPPING])

/** Whether the workload's run holds CPU, memory and disk on its node. */
export function holdsCapacity(workload: Workload): boolean {
    return HOLDING_STATES.has(workload.status)
}

/**
 * What those of the given workloads whose run holds its room hold of their nodes, per owner
 * {@code ownerOf} names, largest CPU share first.
 */
export function allocationBy(workloads: Workload[], ownerOf: (workload: Workload) => string): Allocation[] {
    const byOwner = new Map<string, Allocation>()
    for (const workload of workloads.filter(holdsCapacity)) {
        const owner = ownerOf(workload)
        const allocation = byOwner.get(owner) ?? { owner, workloads: 0, cpus: 0, memoryMb: 0, diskMb: 0 }
        allocation.workloads += 1
        allocation.cpus += workload.cpus
        allocation.memoryMb += workload.memoryMb
        allocation.diskMb += workload.diskSizeMb
        byOwner.set(owner, allocation)
    }
    return [...byOwner.values()].sort((a, b) => b.cpus - a.cpus || b.memoryMb - a.memoryMb)
}

/** The allocation as a table cell reads it, e.g. 1.5 CPU · 3.0 GB. */
export function formatAllocation(allocation: Allocation): string {
    return `${formatCpus(allocation.cpus)} CPU · ${formatMb(allocation.memoryMb)}`
}
