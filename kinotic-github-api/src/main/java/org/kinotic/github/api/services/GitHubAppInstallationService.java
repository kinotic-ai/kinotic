package org.kinotic.github.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.model.GitHubInstallCompletion;

import java.util.concurrent.CompletableFuture;

/**
 * Service the frontend uses to drive GitHub-linking from the existing Kinotic
 * (STOMP) session. The install round-trip is two RPC calls:
 * <ol>
 *   <li>{@link #startInstall(String)} — stages a single-use {@code state}
 *       token and returns the GitHub install URL the SPA navigates the browser to.</li>
 *   <li>{@link #completeInstall(long, String, String)} — called by the SPA's callback
 *       route once GitHub redirects the browser back; consumes the staged state,
 *       verifies the authorizing GitHub user controls the claimed installation,
 *       persists the installation row, and returns the SPA-supplied {@code returnTo}
 *       so the SPA can drive the next-action UX.</li>
 * </ol>
 * Org-scoped via {@code OrganizationScoped} on {@link GitHubAppInstallation}.
 * <p>
 * The installation row is derived from GitHub rather than authored by a caller, so this
 * interface declares its own operations instead of extending {@code IdentifiableCrudService}:
 * every method a published interface reaches is remotely invokable, and an inherited
 * {@code save} would let a caller bind an arbitrary GitHub installation id to their
 * organization without completing the round-trip below.
 */
@Publish
public interface GitHubAppInstallationService {

    /**
     * Stages a single-use {@code state} token bound to the caller's organization in
     * a cluster-wide store, then returns the GitHub install URL with that state
     * embedded. The SPA performs {@code window.location = url}.
     * <p>
     * Caller must be authenticated under {@code ORGANIZATION} scope; the org is read
     * from the participant. The state expires after 10 minutes if unused.
     *
     * @param returnTo SPA route the user wants to land back on after the install
     *                 completes; echoed back from {@link #completeInstall(long, String, String)}.
     *                 May carry query params (e.g. {@code /projects?openNewProject=1})
     *                 to signal "what to do on arrival" to the destination page. May be null.
     */
    CompletableFuture<String> startInstall(String returnTo);

    /**
     * Finalises the install once GitHub has redirected the browser back to the SPA
     * callback. Consumes the staged {@code state}, exchanges the redirect's {@code code}
     * for the authorizing user's access token, and persists the installation only when
     * GitHub reports that user can access it. Returns the persisted row plus the
     * original returnTo so the SPA can drive the post-install UX.
     * <p>
     * An organization holds one installation at a time. Re-completing for the installation
     * already bound refreshes it; binding a second one requires {@link #unlink()} first, so
     * {@link #findForCurrentOrg()} always has a single row to return.
     *
     * @param installationId the {@code installation_id} from GitHub's redirect
     * @param state          the single-use state token echoed by GitHub's redirect
     * @param code           the user-authorization code from GitHub's redirect
     * @throws IllegalStateException when the state is missing/expired/already consumed,
     *                               when {@code code} is absent, or when the caller's org
     *                               is already linked to a different installation
     * @throws org.kinotic.core.api.exceptions.AuthorizationException when the staged org
     *         doesn't match the caller's org, or when the authorizing GitHub user cannot
     *         access the claimed installation
     */
    CompletableFuture<GitHubInstallCompletion> completeInstall(long installationId, String state, String code);

    /**
     * Returns the installation bound to the caller's organization, or {@code null} if
     * GitHub is not yet linked. Drives the "linked / not linked" indicator in the
     * org-settings UI.
     */
    CompletableFuture<GitHubAppInstallation> findForCurrentOrg();

    /**
     * Removes the caller's organization's GitHub link, waiting for the removal to be visible
     * to {@link #findForCurrentOrg()} before completing. No-op when nothing is linked. The
     * organization can link again through {@link #startInstall(String)}.
     */
    CompletableFuture<Void> unlink();
}
