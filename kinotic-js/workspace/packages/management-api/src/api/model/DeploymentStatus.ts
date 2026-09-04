import { DeploymentStatusType } from '@/api/model/DeploymentStatusType'

/**
 * Lifecycle state of something the platform deploys, and why when something is wrong. One
 * shape serves a project's deployment, a microservice's VM, a UI's site and an organization's
 * storage.
 */
export interface DeploymentStatus {
    /**
     * The lifecycle state.
     */
    type: DeploymentStatusType
    /**
     * Why it is in this state, or null when the state speaks for itself; typically the
     * failure that left it FAILED.
     */
    message: string | null
}
