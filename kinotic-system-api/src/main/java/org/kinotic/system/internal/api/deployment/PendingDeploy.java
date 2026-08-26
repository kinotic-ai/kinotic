package org.kinotic.system.internal.api.deployment;

/**
 * The newest push waiting for a project's in-flight deployment to finish.
 */
public record PendingDeploy(String organizationId, String commitSha) {
}
