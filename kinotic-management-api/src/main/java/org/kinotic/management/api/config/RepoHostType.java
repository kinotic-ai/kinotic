package org.kinotic.management.api.config;

/**
 * Where the system hosts the Git repository behind each project.
 */
public enum RepoHostType {

    /**
     * GitHub, through the system's GitHub App: a project's repository is in the GitHub account its organization
     * linked by installing the App.
     */
    GITHUB,

    /**
     * A Gitea server the system administers, as a demonstration server runs beside it: each organization is a
     * Gitea organization holding its projects' repositories, and nothing is linked.
     */
    GITEA

}
