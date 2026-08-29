package org.kinotic.management.api.model;

/**
 * Lifecycle state of a {@link ProjectDeployment}, and why when something is wrong.
 *
 * @param type    the lifecycle state of the deployment
 * @param message why the deployment is in this state, or null when the state speaks for
 *                itself — typically the failure reason of the last deployment job
 */
public record ProjectDeploymentStatus(ProjectDeploymentStatusType type, String message) {

    public ProjectDeploymentStatus(ProjectDeploymentStatusType type) {
        this(type, null);
    }
}
