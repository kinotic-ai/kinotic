import { ProjectDeploymentStatusType } from '@/api/model/ProjectDeploymentStatusType'

/**
 * Lifecycle state of a ProjectDeployment, and why when something is wrong.
 */
export interface ProjectDeploymentStatus {
    /**
     * The lifecycle state of the deployment.
     */
    type: ProjectDeploymentStatusType
    /**
     * Why the deployment is in this state, or null when the state speaks for itself —
     * typically the failure reason of the last deployment job.
     */
    message: string | null
}
