/**
 * Lifecycle state of a ProjectDeployment.
 */
export enum ProjectDeploymentStatusType {
    /**
     * A deployment job is currently syncing the project's code to its node.
     */
    DEPLOYING = 'DEPLOYING',
    /**
     * The project's runtime workload is serving the commit recorded on the deployment.
     */
    RUNNING = 'RUNNING',
    /**
     * The last deployment job failed; the deployment may have no usable runtime workload.
     */
    FAILED = 'FAILED'
}
