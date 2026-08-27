package org.kinotic.domain.api.model;

/**
 * Lifecycle state of a {@link ProjectDeployment}.
 */
public enum ProjectDeploymentStatusType {

    /**
     * A deployment job is currently syncing the project's code to its node.
     */
    DEPLOYING,

    /**
     * The project's runtime workload is serving the commit recorded on the deployment.
     */
    RUNNING,

    /**
     * The last deployment job failed; the deployment may have no usable runtime workload.
     */
    FAILED

}
