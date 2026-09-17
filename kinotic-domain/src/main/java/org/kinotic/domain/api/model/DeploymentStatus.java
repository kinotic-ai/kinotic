package org.kinotic.domain.api.model;

/**
 * Lifecycle state of something the platform deploys, and why when something is wrong. One
 * shape serves a project's deployment, a microservice's VM, a UI's site and an organization's
 * storage.
 *
 * @param type    the lifecycle state
 * @param message why it is in this state, or null when the state speaks for itself;
 *                typically the failure that left it {@link DeploymentStatusType#FAILED}
 */
public record DeploymentStatus(DeploymentStatusType type, String message) {

    public DeploymentStatus(DeploymentStatusType type) {
        this(type, null);
    }
}
