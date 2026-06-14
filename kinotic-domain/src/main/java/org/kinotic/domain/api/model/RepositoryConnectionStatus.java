package org.kinotic.domain.api.model;

/**
 * Connection state between a Kinotic {@link Project} and its backing GitHub repository.
 * Stamped {@link #CONNECTED} when the repo is provisioned and its baseline rendered;
 * left {@link #INITIALIZATION_FAILED} when the repo was created but rendering the
 * baseline did not complete; flipped to {@link #DISCONNECTED} when GitHub revokes the
 * platform's access to the repo (e.g. {@code installation_repositories.removed}).
 */
public enum RepositoryConnectionStatus {

    /** Backing repo is reachable through the org's GitHub installation and its baseline is rendered. */
    CONNECTED,

    /**
     * Backing repo was created but its rendered baseline was not committed. Initialization
     * can be re-run against the existing repo to reach {@link #CONNECTED}.
     */
    INITIALIZATION_FAILED,

    /** Installation no longer has access to the repo; operator must re-link. */
    DISCONNECTED
}
