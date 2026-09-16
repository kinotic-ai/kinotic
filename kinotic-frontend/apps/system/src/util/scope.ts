/**
 * Where a console page sits: the platform when nothing is set, an organization, one of its
 * applications, or one of its projects. Pages that exist in every scope (workloads, jobs,
 * observability) take one as their props and nest their paths under {@link scopePath}.
 */
export interface Scope {
    organizationId?: string
    applicationId?: string
    projectId?: string
}

export function organizationPath(organizationId: string): string {
    return `/organizations/${encodeURIComponent(organizationId)}`
}

export function applicationPath(organizationId: string, applicationId: string): string {
    return `${organizationPath(organizationId)}/applications/${encodeURIComponent(applicationId)}`
}

export function projectPath(organizationId: string, applicationId: string, projectId: string): string {
    return `${applicationPath(organizationId, applicationId)}/project/${encodeURIComponent(projectId)}`
}

/** The path every page of the scope nests under; empty for the platform. */
export function scopePath(scope: Scope): string {
    let ret: string
    if (scope.organizationId && scope.applicationId && scope.projectId) {
        ret = projectPath(scope.organizationId, scope.applicationId, scope.projectId)
    } else if (scope.organizationId && scope.applicationId) {
        ret = applicationPath(scope.organizationId, scope.applicationId)
    } else if (scope.organizationId) {
        ret = organizationPath(scope.organizationId)
    } else {
        ret = ''
    }
    return ret
}

/** How a sentence names the scope: the id of the narrowest thing set, or the platform. */
export function scopeName(scope: Scope): string {
    return scope.projectId ?? scope.applicationId ?? scope.organizationId ?? 'the platform'
}
