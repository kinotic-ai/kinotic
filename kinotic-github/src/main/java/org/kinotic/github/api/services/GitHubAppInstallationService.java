package org.kinotic.github.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.model.GitHubInstallCompletion;

import java.util.concurrent.CompletableFuture;

/**
 * Service the frontend uses to drive GitHub-linking from the existing Kinotic
 * (STOMP) session. The install round-trip is two RPC calls:
 * <ol>
 *   <li>{@link #startInstall(String, String)} — stages a single-use {@code state}
 *       token and returns the GitHub install URL the SPA navigates the browser to.</li>
 *   <li>{@link #completeInstall(long, String)} — called by the SPA's callback route
 *       once GitHub redirects the browser back; consumes the staged state, persists
 *       the installation row, and returns the SPA-supplied {@code intent}/{@code returnTo}
 *       so the SPA can drive the next-action UX.</li>
 * </ol>
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
     *
     * @param intent   free-form string the SPA defines (e.g. {@code "openNewProject"});
     *                 echoed back from {@link #completeInstall(long, String)}. May be null.
     * @param returnTo SPA route the user wants to land back on after the install
     *                 completes; echoed back from {@link #completeInstall(long, String)}.
     *                 May be null.
     */
    CompletableFuture<String> startInstall(String intent, String returnTo);

    /**
     * Finalises the install once GitHub has redirected the browser back to the SPA
     * callback. Consumes the staged {@code state} (must match what was minted by
     * {@link #startInstall(String, String)} for the caller's org), fetches the install
     * details from GitHub, and persists the {@link GitHubAppInstallation} row.
     * Returns the persisted row plus the original intent and returnTo so the SPA can
     * drive the post-install UX.
     *
     * @throws IllegalStateException when the state is missing/expired/already consumed,
     *                               or when its staged org doesn't match the caller's org
     */
    CompletableFuture<GitHubInstallCompletion> completeInstall(long installationId, String state);

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
