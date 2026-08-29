package org.kinotic.system.internal.api.model.deployment;

/**
 * The newest push waiting for a project's in-flight deployment to finish.
 */
public record PendingDeploy(String organizationId, String commitSha) {
}
