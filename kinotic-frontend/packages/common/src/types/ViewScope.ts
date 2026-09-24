/**
 * Where a view sits: the platform when nothing is set, an organization, one of its
 * applications, or one of its projects. A view names its scope once, so the rows it lists
 * leave those ids unsaid and name only what the scope does not.
 */
export interface ViewScope {
    organizationId?: string
    applicationId?: string
    projectId?: string
}
