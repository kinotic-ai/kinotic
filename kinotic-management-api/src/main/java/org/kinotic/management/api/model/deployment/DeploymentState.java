package org.kinotic.management.api.model.deployment;

/**
 * The reconciled state of something the platform deploys from a commit: the shape both the intent
 * and the observation take, so a deployment is in its desired state exactly when the two are
 * equal. Intent names a steady phase and the commit it wants; observation names the phase the
 * deployment is in and the commit it serves.
 *
 * @param phase     the lifecycle state
 * @param commitSha the commit, or null while none is served
 */
public record DeploymentState(DeploymentStatusType phase, String commitSha) {

    /**
     * The commit a state serves, or null when there is no state: what stays live through a
     * deployment that has not finished, or failed.
     */
    public static String commitOf(DeploymentState state) {
        return state == null ? null : state.commitSha();
    }
}
