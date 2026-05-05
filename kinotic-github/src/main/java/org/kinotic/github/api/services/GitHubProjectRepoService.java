package org.kinotic.github.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.github.api.model.GitHubRepoToken;

import java.util.concurrent.CompletableFuture;

/**
 * Operations against a Kinotic Project's backing GitHub repository: minting
 * short-lived installation tokens for worker clones, and creating tags or
 * branches as part of release flows.
 */
@Publish
public interface GitHubProjectRepoService {

    /**
     * Issues a {@code contents:read}-scoped installation token a worker can use
     * to clone the project's backing repo. The token is short-lived (~1 hour);
     * workers should not cache it across job runs.
     *
     * @param organizationId the caller's Kinotic org id; must equal the authenticated
     *                       participant's org
     * @param projectId      the project whose repo the worker wants to clone
     * @return token + expiry + clone URL + default branch, or a failed future if the
     *         project has no GitHub repo provisioned
     */
    CompletableFuture<GitHubRepoToken> issueRepoToken(String organizationId,
                                                      String projectId);

    /**
     * Creates a lightweight tag on the project's backing repo.
     *
     * @param organizationId the caller's Kinotic org id (must match the authenticated
     *                       participant's org)
     * @param projectId      the project whose repo to tag
     * @param tagName        e.g. {@code v1.2.0}
     * @param sha            full 40-character commit SHA the tag should point at
     */
    CompletableFuture<Void> createTag(String organizationId,
                                      String projectId,
                                      String tagName,
                                      String sha);

    /**
     * Creates a branch on the project's backing repo pointing at {@code sha}.
     */
    CompletableFuture<Void> createBranch(String organizationId,
                                         String projectId,
                                         String branchName,
                                         String sha);
}
