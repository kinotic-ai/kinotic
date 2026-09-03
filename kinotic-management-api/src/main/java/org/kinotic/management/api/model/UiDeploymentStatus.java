package org.kinotic.management.api.model;

/**
 * Lifecycle state of a {@link UiDeployment}, and why when something is wrong.
 *
 * @param type    the lifecycle state of the deployment
 * @param message why the deployment is in this state, or null when the state speaks for
 *                itself; typically the failure that left it {@link UiDeploymentStatusType#FAILED}
 */
public record UiDeploymentStatus(UiDeploymentStatusType type, String message) {

    public UiDeploymentStatus(UiDeploymentStatusType type) {
        this(type, null);
    }
}
