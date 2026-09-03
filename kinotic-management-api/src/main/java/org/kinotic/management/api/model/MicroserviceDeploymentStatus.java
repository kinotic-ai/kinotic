package org.kinotic.management.api.model;

/**
 * Lifecycle state of a {@link MicroserviceDeployment}, and why when something is wrong.
 *
 * @param type    the lifecycle state of the deployment
 * @param message why the deployment is in this state, or null when the state speaks for
 *                itself; typically the failure that left it
 *                {@link MicroserviceDeploymentStatusType#FAILED}
 */
public record MicroserviceDeploymentStatus(MicroserviceDeploymentStatusType type, String message) {

    public MicroserviceDeploymentStatus(MicroserviceDeploymentStatusType type) {
        this(type, null);
    }
}
