import type { ViewScope } from '@kinotic-ai/frontend-common'

export function applicationPath(applicationId: string): string {
    return `/application/${encodeURIComponent(applicationId)}`
}

export function projectPath(applicationId: string, projectId: string): string {
    return `${applicationPath(applicationId)}/project/${encodeURIComponent(projectId)}`
}

/** The path every page of the scope nests under; empty for the organization, whose pages sit at the root. */
export function scopePath(scope: ViewScope): string {
    let ret: string
    if (scope.applicationId && scope.projectId) {
        ret = projectPath(scope.applicationId, scope.projectId)
    } else if (scope.applicationId) {
        ret = applicationPath(scope.applicationId)
    } else {
        ret = ''
    }
    return ret
}

/** The page of one workload, under the scope's Workloads list. */
export function workloadPath(scope: ViewScope, workloadId: string): string {
    return `${scopePath(scope)}/workloads/${encodeURIComponent(workloadId)}`
}

/** How a sentence names the scope: the id of the narrowest thing set, or the organization. */
export function scopeName(scope: ViewScope): string {
    return scope.projectId ?? scope.applicationId ?? 'your organization'
}
