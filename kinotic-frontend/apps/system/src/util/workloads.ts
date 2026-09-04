import { Direction, Kinotic, Order, Pageable, Sort } from '@kinotic-ai/core'
import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import type { Scope } from './scope'

/**
 * How many workloads a scan reads. A platform with more is undercounted until a server-side
 * aggregation exists.
 */
export const WORKLOAD_SCAN_LIMIT = 1000

const SCAN_PAGE_SIZE = 100

/** The organization filter value that keeps the platform's own workloads, the ones with no organization. */
export const PLATFORM_ONLY = 'platform'

/** The states in the order every breakdown lists them: the live ones first, then the ended ones. */
export const WORKLOAD_STATES: WorkloadStatus[] = [
    WorkloadStatus.RUNNING,
    WorkloadStatus.STARTING,
    WorkloadStatus.PENDING,
    WorkloadStatus.STOPPING,
    WorkloadStatus.COMPLETED,
    WorkloadStatus.STOPPED,
    WorkloadStatus.FAILED
]

/** What narrows a scan beyond its scope. */
export interface WorkloadScanOptions {
    /** Keeps only the workloads that belong to no organization, the platform's own. */
    platformOnly?: boolean
    /** Keeps only the workloads placed on this node. */
    nodeId?: string
}

/** Maps a workload status to the PrimeVue Tag severity it renders with. */
export function workloadSeverity(status: WorkloadStatus): string {
    let ret: string
    if (status === WorkloadStatus.RUNNING || status === WorkloadStatus.COMPLETED) {
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

/** Whether the workload has a run in progress that stopWorkload can end. */
export function canStop(workload: Pick<Workload, 'status'>): boolean {
    return workload.status === WorkloadStatus.RUNNING || workload.status === WorkloadStatus.STARTING
}

/** Whether restartWorkload can boot the workload's VM again. */
export function canRestart(workload: Pick<Workload, 'status' | 'autoRemove'>): boolean {
    // A run that ended with autoRemove has no VM left to restart
    const dormant = (workload.status === WorkloadStatus.STOPPED || workload.status === WorkloadStatus.COMPLETED)
        && !workload.autoRemove
    return dormant || workload.status === WorkloadStatus.FAILED
}

export function countByStatus(workloads: Workload[]): Record<WorkloadStatus, number> {
    const ret = Object.fromEntries(WORKLOAD_STATES.map(state => [state, 0])) as Record<WorkloadStatus, number>
    for (const workload of workloads) {
        ret[workload.status] += 1
    }
    return ret
}

/**
 * Whether the deployment job started the workload for the project. ProjectDeployJobDefinitionFactory
 * names the sync, publish and runtime workloads it starts after the project, which is the only
 * record of the project a workload carries.
 */
export function belongsToProject(workload: Workload, projectId: string): boolean {
    return workload.name === `project-sync-${projectId}`
        || workload.name === `project-ui-publish-${projectId}`
        || workload.name.startsWith(`project-runtime-${projectId}-`)
}

/** The image without its registry host, as the tables show it. */
export function shortImage(image: string): string {
    return image.replace(/^[^/]+\.[^/]+\//, '')
}

function term(field: string, value: string): string {
    return `${field}:"${value.replace(/["\\]/g, '\\$&')}"`
}

/**
 * Every workload in the scope, newest first, narrowed further by the options. The organization,
 * application and node narrow the search server-side; the project is matched by name on what
 * comes back.
 */
export async function scanWorkloads(scope: Scope, options: WorkloadScanOptions = {}): Promise<Workload[]> {
    const terms: string[] = []
    if (scope.organizationId) terms.push(term('organizationId', scope.organizationId))
    if (scope.applicationId) terms.push(term('applicationId', scope.applicationId))
    if (options.nodeId) terms.push(term('nodeId', options.nodeId))
    if (options.platformOnly) terms.push('NOT _exists_:organizationId')
    // a query_string of negative clauses alone matches nothing, so the match-all anchors it
    const query = terms.length > 0 ? ['*:*', ...terms].join(' AND ') : null
    const sort = new Sort()
    sort.orders = [new Order('created', Direction.DESC)]
    const ret: Workload[] = []
    for (let pageNumber = 0; ret.length < WORKLOAD_SCAN_LIMIT; pageNumber++) {
        const pageable = Pageable.create(pageNumber, SCAN_PAGE_SIZE, sort)
        const page = query
            ? await Kinotic.workloads.search(query, pageable)
            : await Kinotic.workloads.findAll(pageable)
        const content = page.content ?? []
        ret.push(...content)
        if (content.length < SCAN_PAGE_SIZE) {
            break
        }
    }
    const projectId = scope.projectId
    return projectId ? ret.filter(workload => belongsToProject(workload, projectId)) : ret
}
