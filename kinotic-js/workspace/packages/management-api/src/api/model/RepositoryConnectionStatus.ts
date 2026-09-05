/**
 * Connection state between a project and its backing GitHub repository.
 * Stamped {@link RepositoryConnectionStatus.CONNECTED} when the repo is provisioned and
 * its baseline rendered; left {@link RepositoryConnectionStatus.INITIALIZATION_FAILED}
 * when the repo was created but rendering the baseline did not complete;
 * {@link RepositoryConnectionStatus.DISCONNECTED} when GitHub revokes the platform's
 * access to the repo.
 */
export enum RepositoryConnectionStatus {

    /** Backing repo is reachable through the org's GitHub installation and its baseline is rendered. */
    CONNECTED = 'CONNECTED',

    /**
     * Backing repo was created but its rendered baseline was not committed. Initialization
     * can be re-run against the existing repo to reach {@link RepositoryConnectionStatus.CONNECTED}.
     */
    INITIALIZATION_FAILED = 'INITIALIZATION_FAILED',

    /** Installation no longer has access to the repo; operator must re-link. */
    DISCONNECTED = 'DISCONNECTED',
}
