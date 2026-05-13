package org.kinotic.os.api.model;

/**
 * Connection state between a Kinotic {@link Project} and its backing GitHub repository.
 * Stamped {@link #CONNECTED} when the repo is provisioned; flipped to
 * {@link #DISCONNECTED} when GitHub revokes the platform's access to the repo
 * (e.g. {@code installation_repositories.removed}).
 */
public enum RepositoryConnectionStatus {

    /** Backing repo is reachable through the org's GitHub installation. */
    CONNECTED,

    /** Installation no longer has access to the repo; operator must re-link. */
    DISCONNECTED
}
