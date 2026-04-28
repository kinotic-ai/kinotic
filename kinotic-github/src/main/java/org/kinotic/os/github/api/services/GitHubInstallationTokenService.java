package org.kinotic.os.github.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Scope;
import org.kinotic.os.github.api.model.IssuedInstallationToken;

import java.util.concurrent.CompletableFuture;

/**
 * Mints short-lived GitHub installation tokens that worker nodes use to clone the
 * repository linked to a Kinotic Project. Workers never see the App private key —
 * only the per-clone token, scoped to a single repository with {@code contents:read}.
 * <p>
 * Scoped by {@code organizationId} so the in-memory token cache stays hot on one
 * node per org.
 */
@Publish
public interface GitHubInstallationTokenService {

    /**
     * Issues a clone-scoped installation token for the project's linked repo.
     *
     * @param organizationId the caller's Kinotic Org id; must equal the authenticated
     *                       participant's org. Carried as {@link Scope} so service
     *                       routing pins all calls for one org to one node.
     * @param projectId      the project whose linked repo the worker wants to clone
     * @return token + expiry + clone URL, or a failed future with
     *         {@code IllegalStateException} when the project has no GitHub link
     */
    CompletableFuture<IssuedInstallationToken> issueRepoToken(@Scope String organizationId,
                                                              String projectId);
}
