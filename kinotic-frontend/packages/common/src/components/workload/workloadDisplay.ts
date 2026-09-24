import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'

/** The states in the order every breakdown lists them. */
export const WORKLOAD_STATES: WorkloadStatus[] = [
    WorkloadStatus.RUNNING,
    WorkloadStatus.STARTING,
    WorkloadStatus.PENDING,
    WorkloadStatus.STOPPING,
    WorkloadStatus.STOPPED,
    WorkloadStatus.FAILED
]

/** Maps a workload status to the PrimeVue Tag severity it renders with. */
export function workloadSeverity(status: WorkloadStatus): string {
    let ret: string
    if (status === WorkloadStatus.RUNNING) {
        ret = 'success'
    } else if (status === WorkloadStatus.STARTING || status === WorkloadStatus.PENDING) {
        ret = 'info'
    } else if (status === WorkloadStatus.STOPPING) {
        ret = 'warn'
    } else if (status === WorkloadStatus.FAILED) {
        ret = 'danger'
    } else {
        ret = 'secondary'
    }
    return ret
}

/** A state's name as a word, e.g. Running. */
export function workloadStateLabel(status: WorkloadStatus): string {
    return status.charAt(0) + status.slice(1).toLowerCase()
}

export function countByStatus(workloads: Workload[]): Record<WorkloadStatus, number> {
    const ret = Object.fromEntries(WORKLOAD_STATES.map(state => [state, 0])) as Record<WorkloadStatus, number>
    for (const workload of workloads) {
        ret[workload.status] += 1
    }
    return ret
}

/** The image without its registry host, as the tables show it. */
export function shortImage(image: string): string {
    return image.replace(/^[^/]+\.[^/]+\//, '')
}
