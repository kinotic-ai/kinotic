package org.kinotic.github.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.github.api.model.GitHubAppInstallation;

import java.util.concurrent.CompletableFuture;

/**
 * Service the frontend uses to drive GitHub-linking from the existing Kinotic
 * (STOMP) session: starts the install, reads link state, unlinks. The actual GitHub
 * roundtrip lands on a single REST callback ({@code /api/github/install/callback})
 * that GitHub itself drives — but everything user-initiated is a Kinotic RPC, so the
 * short-lived JWT used for the STOMP CONNECT never needs to be replayed on REST.
 * <p>
 * Org-scoped via {@code OrganizationScoped} on {@link GitHubAppInstallation}.
 */
@Publish
public interface GitHubAppInstallationService extends IdentifiableCrudService<GitHubAppInstallation, String> {

    /**
     * Stages a single-use {@code state} token bound to the caller's organization in
     * a cluster-wide store, then returns the GitHub install URL with that state
     * embedded. The SPA performs {@code window.location = url}.
     * <p>
     * Caller must be authenticated under {@code ORGANIZATION} scope; the org is read
     * from the participant. The state expires after 10 minutes if unused.
     */
    CompletableFuture<String> startInstall();

    /**
     * Returns the (at-most-one) installation bound to the caller's organization, or
     * {@code null} if GitHub is not yet linked. Drives the "linked / not linked"
     * indicator in the org-settings UI.
     */
    CompletableFuture<GitHubAppInstallation> findForCurrentOrg();

    /**
     * Cross-org lookup by the GitHub-side installation id. Returns {@code null} when no
     * Kinotic org has bound this installation. Webhook handlers use this to map an
     * inbound delivery to the org that owns the install — the delivery has no Kinotic
     * participant attached, so call this inside
     * {@code SecurityContext.withElevatedAccess(...)}.
     */
    CompletableFuture<GitHubAppInstallation> findByGithubInstallationId(long githubInstallationId);
}
