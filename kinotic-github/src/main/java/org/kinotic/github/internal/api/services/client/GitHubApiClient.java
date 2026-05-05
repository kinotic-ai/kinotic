package org.kinotic.github.internal.api.services.client;

import io.vertx.core.Future;
import org.kinotic.github.api.model.GitHubToken;

import java.util.Map;

/**
 * Single concentration point for GitHub REST API calls used by the platform: minting
 * installation access tokens, looking up install metadata, creating repos from a
 * template, creating tags / branches.
 * <p>
 * Token mints are cached and deduped — concurrent callers asking for a token with
 * the same {@code (installationId, repoId, permissions)} key share the in-flight
 * request, and any returned token is guaranteed to have at least 10 minutes of
 * life remaining.
 */
public interface GitHubApiClient {

    /** Standard token scopes used in the platform. */
    Map<String, String> READ_CONTENTS = Map.of("contents", "read");
    Map<String, String> WRITE_CONTENTS = Map.of("contents", "write");

    /**
     * Creates a ref on {@code repoFullName}. Idempotent: a 422 with body
     * "Reference already exists" succeeds.
     *
     * @param installationToken token with {@code contents:write} on the target repo
     * @param repoFullName      {@code owner/repo} of the target repo
     * @param refName           fully qualified ref, e.g. {@code refs/tags/v1.2.0}
     *                          or {@code refs/heads/release-2026Q2}
     * @param sha               full 40-character commit SHA the ref should point at
     */
    Future<Void> createRef(String installationToken,
                           String repoFullName,
                           String refName,
                           String sha);

    /**
     * Creates a new repository under {@code owner} from {@code templateFullName}.
     * The App must have {@code Administration: Write} permission on the target
     * owner. Fails if a repo with the given name already exists under the owner.
     *
     * @param installationToken token scoped to the installation that has access to
     *                          the template and the target owner
     * @param templateFullName  {@code owner/repo} of the template
     * @param owner             target account login (user or org)
     * @param name              new repo name (must satisfy GitHub's name rules)
     * @param description       optional repo description
     * @param isPrivate         visibility of the new repo
     */
    Future<CreatedRepository> createRepoFromTemplate(String installationToken,
                                                     String templateFullName,
                                                     String owner,
                                                     String name,
                                                     String description,
                                                     boolean isPrivate);

    /**
     * Looks up the install metadata GitHub holds for {@code installationId}. Used at
     * link time to capture the owning account's login + type for our own record.
     */
    Future<InstallationDetails> getInstallation(long installationId);

    /**
     * Returns a cached or freshly-minted installation access token whose remaining
     * life exceeds 10 minutes. Restricting {@code repoId} + {@code permissions}
     * produces a token that cannot exceed the requested permissions even if
     * intercepted; pass {@code null} for {@code repoId} when the operation targets
     * the installation rather than a specific repo (e.g. creating a new repo from
     * a template).
     *
     * @param installationId GitHub-side installation id (not the Kinotic doc id)
     * @param repoId         GitHub repository id, or {@code null} for an
     *                       installation-wide token
     * @param permissions    e.g. {@link #READ_CONTENTS} or {@link #WRITE_CONTENTS}
     */
    Future<GitHubToken> getToken(long installationId,
                                 Long repoId,
                                 Map<String, String> permissions);
}
