import { UiDeploymentStatusType } from '@/api/model/UiDeploymentStatusType'

/**
 * Lifecycle state of a UiDeployment, and why when something is wrong.
 */
export interface UiDeploymentStatus {
    /**
     * The lifecycle state of the deployment.
     */
    type: UiDeploymentStatusType
    /**
     * Why the deployment is in this state, or null when the state speaks for itself;
     * typically the failure that left it FAILED.
     */
    message: string | null
}
