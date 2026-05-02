package org.kinotic.github.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Scope;

import java.util.concurrent.CompletableFuture;

/**
 * Creates tags and branches on the GitHub repository linked to a Kinotic Project. The
 * platform itself does not commit code — these refs point at SHAs that already exist
 * on the repository (typically pushed by the Kinotic build/release flow). Idempotent:
 * recreating an existing ref with the same target SHA returns success.
 */
@Publish
public interface GitHubRefService {

    /**
     * Creates a lightweight tag {@code tagName} pointing at the given commit SHA on
     * the project's linked repo.
     *
     * @param organizationId the caller's Kinotic Org id (must match the authenticated
     *                       participant's org)
     * @param projectId      the project whose linked repo to tag
     * @param tagName        e.g. {@code v1.2.0}
     * @param sha            full 40-character commit SHA the tag should point at
     */
    CompletableFuture<Void> createTag(@Scope String organizationId,
                                      String projectId,
                                      String tagName,
                                      String sha);

    /**
     * Creates a branch {@code branchName} pointing at {@code sha} on the project's
     * linked repo.
     */
    CompletableFuture<Void> createBranch(@Scope String organizationId,
                                         String projectId,
                                         String branchName,
                                         String sha);
}
