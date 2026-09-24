import type { RouteLocationRaw } from 'vue-router'
import { Kinotic } from '@kinotic-ai/core'
import type { Workload } from '@kinotic-ai/management-api'
import { scanWorkloadPages, type ViewScope } from '@kinotic-ai/frontend-common'
import { organizationPath } from './scope'

/** The organization filter value that keeps the platform's own workloads, the ones with no organization. */
export const PLATFORM_ONLY = 'platform'

/**
 * Names the platform's own workloads where workloads are grouped by organization. Ids are
 * lowercase letters, digits and dashes, so no organization can take it.
 */
const PLATFORM_OWNER = 'the platform'

/** The organization a workload runs for, as a grouping by organization names it. */
export function organizationOwnerOf(workload: Workload): string {
    return workload.organizationId ?? PLATFORM_OWNER
}

/** Where an owner of a grouping by organization leads: the organization, or the platform's own workloads. */
export function organizationOwnerRoute(owner: string): RouteLocationRaw {
    return owner === PLATFORM_OWNER ? { path: '/workloads', query: { org: PLATFORM_ONLY } } : organizationPath(owner)
}

/** What narrows a scan beyond its scope. */
export interface WorkloadScanOptions {
    /** Keeps only the workloads that belong to no organization, the platform's own. */
    platformOnly?: boolean
    /** Keeps only the workloads placed on this node. */
    nodeId?: string
}

function term(field: string, value: string): string {
    return `${field}:"${value.replace(/["\\]/g, '\\$&')}"`
}

/**
 * Every workload in the scope, newest first, narrowed further by the options. The organization,
 * application and node narrow the search server-side; the project is matched by name on what
 * comes back.
 */
export async function scanWorkloads(scope: ViewScope, options: WorkloadScanOptions = {}): Promise<Workload[]> {
    const terms: string[] = []
    if (scope.organizationId) terms.push(term('organizationId', scope.organizationId))
    if (scope.applicationId) terms.push(term('applicationId', scope.applicationId))
    if (options.nodeId) terms.push(term('nodeId', options.nodeId))
    if (options.platformOnly) terms.push('NOT _exists_:organizationId')
    // a query_string of negative clauses alone matches nothing, so the match-all anchors it
    const query = terms.length > 0 ? ['*:*', ...terms].join(' AND ') : null
    return scanWorkloadPages(pageable => query ? Kinotic.workloads.search(query, pageable) : Kinotic.workloads.findAll(pageable),
                             scope.projectId)
}
