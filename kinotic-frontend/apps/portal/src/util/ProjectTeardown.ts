import type { Project } from '@kinotic-ai/management-api'

/** What deleting one project removes beyond the project itself, as its deployment holds it now. */
export interface ProjectTeardown {
    project: Project
    /** True when the project has a deployment, which the delete tears down. */
    deployed: boolean
    microservices: string[]
    uis: string[]
    /** The machine identities the deployment provisioned, by display name. */
    machines: string[]
    /** True when the project has a dependency inventory (SBOM). */
    hasDependencies: boolean
}
