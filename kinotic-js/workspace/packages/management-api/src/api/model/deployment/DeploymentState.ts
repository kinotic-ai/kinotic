import type { DeploymentStatusType } from '@/api/model/deployment/DeploymentStatusType'

/**
 * The reconciled state of something the platform deploys from a commit: the shape both the intent
 * and the observation take, so a deployment is in its desired state exactly when the two are equal.
 */
export interface DeploymentState {
    /** The lifecycle state. */
    phase: DeploymentStatusType
    /** The commit, or null while none is served. */
    commitSha: string | null
}
