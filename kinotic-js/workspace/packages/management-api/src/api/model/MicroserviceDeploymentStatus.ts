import { MicroserviceDeploymentStatusType } from '@/api/model/MicroserviceDeploymentStatusType'

/**
 * Lifecycle state of a MicroserviceDeployment, and why when something is wrong.
 */
export interface MicroserviceDeploymentStatus {
    /**
     * The lifecycle state of the deployment.
     */
    type: MicroserviceDeploymentStatusType
    /**
     * Why the deployment is in this state, or null when the state speaks for itself;
     * typically the failure that left it FAILED.
     */
    message: string | null
}
