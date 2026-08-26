package org.kinotic.os.internal.api.services.github.client;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.kinotic.os.api.model.GitHubToken;

import java.util.List;
import java.util.Map;

/**
 * Single concentration point for GitHub REST API calls used by the platform: minting
 * installation access tokens, verifying install ownership at link time, creating
 * repos from a template, creating tags / branches.
 */
public interface GitHubApiClient {

    /** Standard token scopes used in the platform. */
    Map<String, String> READ_CONTENTS = Map.of("contents", "read");
    Map<String, String> WRITE_CONTENTS = Map.of("contents", "write");
    /**
     * Scope for {@link #createRepoFromTemplate}: repo creation is an administration
     * operation, and the generate endpoint additionally requires contents read.
     */
    Map<String, String> CREATE_REPOSITORY = Map.of("administration", "write",
                                                   "contents", "read");

    /**
     * Creates a blob on {@code repoFullName} and returns its SHA, for binary
     * content that cannot ride inline in {@link #createTree}.
     *
     * @param installationToken token with {@code contents:write} on the target repo
     */
    Future<String> createBlob(String installationToken,
                              String repoFullName,
                              byte[] content);

    /**
     * Creates a root commit (no parents) pointing at {@code treeSha} and returns
     * the commit SHA.
     *
     * @param installationToken token with {@code contents:write} on the target repo
     */
    Future<String> createCommit(String installationToken,
                                String repoFullName,
                                String message,
                                String treeSha);

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
     * @param installationToken token with {@link #CREATE_REPOSITORY} scope on an
     *                          installation that has access to the template and the
     *                          target owner
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
     * Creates a complete Git tree (no base tree) on {@code repoFullName} from
     * {@code entries} and returns the tree SHA.
     *
     * @param installationToken token with {@code contents:write} on the target repo
     */
    Future<String> createTree(String installationToken,
                              String repoFullName,
                              List<TreeEntry> entries);

    /**
     * Downloads {@code ref} of {@code repoFullName} as a gzipped tarball. The
     * archive wraps all paths in a single root directory and fails with a 404
     * while the repo has no content yet.
     *
     * @param installationToken token with {@code contents:read} on the target repo
     * @param ref               branch, tag, or commit SHA
     */
    Future<Buffer> downloadTarball(String installationToken,
                                   String repoFullName,
                                   String ref);

    /**
     * Exchanges a user-authorization {@code code} from an install redirect for a GitHub
     * user access token acting on behalf of the user who authorized in the browser.
     */
    Future<String> exchangeUserAccessCode(String clientId, String clientSecret, String code);

    /**
     * Lists every installation the user behind {@code userAccessToken} has explicit
     * permission to access, paging until GitHub's collection is exhausted.
     */
    Future<List<InstallationDetails>> listUserInstallations(String userAccessToken);

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

    /**
     * Moves an existing ref on {@code repoFullName} to {@code sha}.
     *
     * @param installationToken token with {@code contents:write} on the target repo
     * @param refName           ref without the {@code refs/} prefix, e.g. {@code heads/main}
     * @param force             move the ref even when the update is not fast-forward
     */
    Future<Void> updateRef(String installationToken,
                           String repoFullName,
                           String refName,
                           String sha,
                           boolean force);
}
