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
 *   <li>{@link #startInstall(String)} — stages a single-use {@code state}
 *       token and returns the GitHub install URL the SPA navigates the browser to.</li>
 *   <li>{@link #completeInstall(long, String, String)} — called by the SPA's callback
 *       route once GitHub redirects the browser back; consumes the staged state,
 *       verifies the authorizing GitHub user controls the claimed installation,
 *       persists the installation row, and returns the SPA-supplied {@code returnTo}
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
     * @param returnTo SPA route the user wants to land back on after the install
     *                 completes; echoed back from {@link #completeInstall(long, String, String)}.
     *                 May carry query params (e.g. {@code /projects?openNewProject=1})
     *                 to signal "what to do on arrival" to the destination page. May be null.
     */
    CompletableFuture<String> startInstall(String returnTo);

    /**
     * Finalises the install once GitHub has redirected the browser back to the SPA
     * callback. Consumes the staged {@code state} (must match what was minted by
     * {@link #startInstall(String)} for the caller's org), then proves the claimed
     * installation is controlled by the GitHub user who authorized in this browser:
     * the {@code code} GitHub appended to the redirect is exchanged for that user's
     * access token, and the installation is persisted only when it appears among the
     * installations of this App that GitHub reports the user can access. Returns the
     * persisted row plus the original returnTo so the SPA can drive the post-install
     * UX.
     *
     * @param installationId the {@code installation_id} from GitHub's redirect
     * @param state          the single-use state token echoed by GitHub's redirect
     * @param code           the user-authorization code from GitHub's redirect
     * @throws IllegalStateException when the state is missing/expired/already consumed,
     *                               or when {@code code} is absent
     * @throws org.kinotic.core.api.exceptions.AuthorizationException when the staged org
     *         doesn't match the caller's org, or when the authorizing GitHub user cannot
     *         access the claimed installation
     */
    CompletableFuture<GitHubInstallCompletion> completeInstall(long installationId, String state, String code);

    /**
     * Returns the (at-most-one) installation bound to the caller's organization, or
     * {@code null} if GitHub is not yet linked. Drives the "linked / not linked"
     * indicator in the org-settings UI.
     */
    CompletableFuture<GitHubAppInstallation> findForCurrentOrg();

    /**
     * Looks up the installation with the given GitHub-side installation id within the
     * current participant's organization. Returns {@code null} when the caller's org has not
     * bound an installation with that id.
     */
    CompletableFuture<GitHubAppInstallation> findByGithubInstallationId(long githubInstallationId);
}
