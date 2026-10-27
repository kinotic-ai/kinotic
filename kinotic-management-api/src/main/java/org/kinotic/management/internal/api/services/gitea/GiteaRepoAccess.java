package org.kinotic.management.internal.api.services.gitea;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The access a token issued for a project's Gitea repository grants, and the account it is issued through.
 */
@Getter
@RequiredArgsConstructor
enum GiteaRepoAccess {

    /** Fetching the repository, as a deployment's sync workload does. */
    READ("kr-", "read", "read:repository"),

    /** Pushing to the repository, as a person or a coding agent committing to the project does. */
    WRITE("kw-", "write", "write:repository");

    /** The prefix of the name of the account a token of this access is issued through. */
    private final String accountPrefix;

    /** The collaborator permission the account holds on the repository. */
    private final String permission;

    /** The scope a token of this access carries. */
    private final String scope;

}
