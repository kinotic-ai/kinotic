package org.kinotic.github.api.model;

import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * A {@link GitHubToken} bundled with the repository-clone metadata a worker needs
 * to {@code git clone} the project's backing repo. Returned to workers from
 * {@code GitHubProjectRepoService.issueRepoToken}.
 */
@Getter
@ToString(callSuper = true)
public class GitHubRepoToken extends GitHubToken {

    /** {@code https://github.com/<owner>/<repo>.git} for the project's repo. */
    private final String cloneUrl;

    /** Default branch on the repo (e.g. {@code main}). */
    private final String defaultBranch;

    public GitHubRepoToken(String token, Instant expiresAt, String cloneUrl, String defaultBranch) {
        super(token, expiresAt);
        this.cloneUrl = cloneUrl;
        this.defaultBranch = defaultBranch;
    }
}
