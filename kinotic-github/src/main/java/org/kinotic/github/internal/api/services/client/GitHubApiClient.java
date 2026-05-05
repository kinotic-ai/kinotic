package org.kinotic.github.internal.api.services.client;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.github.api.model.GitHubToken;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Vert.x WebClient wrapper for {@code api.github.com}. The single concentration point
 * for HTTP: builds requests, attaches the right {@code Authorization: Bearer ...}
 * header (App JWT for install-token mint, install token for everything else), parses
 * responses into typed records, surfaces typed errors. Mirrors the structure of
 * {@code DefaultElasticVertxClient} in kinotic-persistence.
 * <p>
 * Installation tokens are cached in-process keyed by
 * {@code (installationId, repoId, permissions)} so a clone-scoped token is not
 * reused for ref creation. Concurrent callers for the same key share the in-flight
 * mint; on every read we additionally enforce that the remaining lifetime exceeds
 * {@link #MIN_RETURNED_TOKEN_LIFETIME} so a worker is never handed a token about
 * to die mid-clone.
 */
@Slf4j
@Component
public class GitHubApiClient {

    private static final String API_HOST = "api.github.com";
    private static final int API_PORT = 443;
    private static final String ACCEPT = "application/vnd.github+json";
    private static final String API_VERSION_HEADER = "X-GitHub-Api-Version";
    private static final String API_VERSION = "2022-11-28";
    private static final String USER_AGENT = "kinotic-platform";

    /** Standard scopes used in the platform. Single source so the cache key dedups them. */
    public static final Map<String, String> READ_CONTENTS = Map.of("contents", "read");
    public static final Map<String, String> WRITE_CONTENTS = Map.of("contents", "write");

    /**
     * Never return a token with less than this much life remaining; evict and reload
     * first. 10 minutes is comfortably above the slowest expected clone of a
     * multi-GB repo.
     */
    private static final Duration MIN_RETURNED_TOKEN_LIFETIME = Duration.ofMinutes(10);

    private final Vertx vertx;
    private final GitHubAppJwtFactory jwtFactory;
    private WebClient webClient;
    private AsyncLoadingCache<TokenKey, GitHubToken> tokenCache;

    public GitHubApiClient(Vertx vertx, GitHubAppJwtFactory jwtFactory) {
        this.vertx = vertx;
        this.jwtFactory = jwtFactory;
    }

    @PostConstruct
    public void start() {
        this.webClient = WebClient.create(vertx, new WebClientOptions()
                .setSsl(true)
                .setUserAgent(USER_AGENT));
        // expireAfterWrite matches GitHub's 60-minute issued lifetime; the per-read
        // freshness check below evicts entries earlier when they're about to die.
        this.tokenCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(50))
                .maximumSize(10_000)
                .buildAsync((TokenKey key, java.util.concurrent.Executor _) ->
                        mintInstallationToken(key.installationId(), key.repoId(), key.permissions())
                                .toCompletionStage()
                                .toCompletableFuture());
    }

    @PreDestroy
    public void stop() {
        if (webClient != null) {
            webClient.close();
        }
    }

    // ── Installation lookup ───────────────────────────────────────────────────

    public Future<InstallationDetails> getInstallation(long installationId) {
        return jwtAuthedGet("/app/installations/" + installationId)
                .compose(resp -> expectJsonObject(resp, "getInstallation"))
                .map(json -> {
                    JsonObject account = json.getJsonObject("account");
                    return new InstallationDetails(
                            json.getLong("id"),
                            account != null ? account.getString("login") : null,
                            account != null ? account.getString("type") : null);
                });
    }

    /**
     * Returns a cached or freshly-minted installation access token whose remaining
     * life exceeds {@link #MIN_RETURNED_TOKEN_LIFETIME}. Restricting {@code repoId} +
     * {@code permissions} produces a token that cannot exceed the requested permissions
     * even if intercepted; pass {@code null} for {@code repoId} when the operation
     * targets the installation rather than a specific repo (e.g. creating a new repo
     * from a template).
     */
    public Future<GitHubToken> getInstallationToken(long installationId,
                                                    Long repoId,
                                                    Map<String, String> permissions) {
        TokenKey key = new TokenKey(installationId, repoId, permissions);
        GitHubToken peek = tokenCache.synchronous().getIfPresent(key);
        if (peek != null && !hasEnoughLife(peek)) {
            tokenCache.synchronous().invalidate(key);
        }
        CompletableFuture<GitHubToken> loaded = tokenCache.get(key);
        return Future.fromCompletionStage(loaded);
    }

    private Future<GitHubToken> mintInstallationToken(long installationId,
                                                      Long repoId,
                                                      Map<String, String> permissions) {
        JsonObject body = new JsonObject();
        if (repoId != null) {
            body.put("repository_ids", new JsonArray().add(repoId));
        }
        if (permissions != null && !permissions.isEmpty()) {
            JsonObject perms = new JsonObject();
            permissions.forEach(perms::put);
            body.put("permissions", perms);
        }
        return jwtAuthedPost("/app/installations/" + installationId + "/access_tokens", body)
                .compose(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        return Future.failedFuture(httpError("createInstallationToken", resp));
                    }
                    JsonObject json = resp.bodyAsJsonObject();
                    return Future.succeededFuture(new GitHubToken(
                            json.getString("token"),
                            Instant.parse(json.getString("expires_at"))));
                });
    }

    private static boolean hasEnoughLife(GitHubToken token) {
        return token.getExpiresAt().isAfter(Instant.now().plus(MIN_RETURNED_TOKEN_LIFETIME));
    }

    // ── Repository creation from a template ───────────────────────────────────

    /**
     * Creates a new repository under {@code owner} from the given template,
     * via {@code POST /repos/{template_owner}/{template_repo}/generate}. The App
     * must have {@code Administration: Write} permission on the target owner
     * for this to succeed.
     *
     * @param installationToken token scoped to the installation that has access to
     *                          the template and the target owner
     * @param templateFullName  {@code owner/repo} of the template
     * @param owner             target account login (user or org)
     * @param name              new repo name (must satisfy GitHub's name rules)
     * @param description       optional repo description
     * @param isPrivate         visibility of the new repo
     */
    public Future<CreatedRepository> createRepoFromTemplate(String installationToken,
                                                            String templateFullName,
                                                            String owner,
                                                            String name,
                                                            String description,
                                                            boolean isPrivate) {
        JsonObject body = new JsonObject()
                .put("owner", owner)
                .put("name", name)
                .put("include_all_branches", false)
                .put("private", isPrivate);
        if (description != null && !description.isBlank()) {
            body.put("description", description);
        }
        return tokenAuthedPost("/repos/" + templateFullName + "/generate", installationToken, body)
                .compose(resp -> {
                    if (resp.statusCode() == 201) {
                        JsonObject json = resp.bodyAsJsonObject();
                        return Future.succeededFuture(new CreatedRepository(
                                json.getLong("id"),
                                json.getString("full_name"),
                                json.getString("default_branch")));
                    }
                    return Future.failedFuture(httpError("createRepoFromTemplate", resp));
                });
    }

    // ── Ref creation ──────────────────────────────────────────────────────────

    /**
     * Creates a ref on {@code repoFullName}.
     * @param refName fully qualified ref, e.g. {@code refs/tags/v1.2.0} or
     *                {@code refs/heads/release-2026Q2}
     * @return a future that completes successfully on 201, or also on 422 "Reference
     *         already exists" (idempotent)
     */
    public Future<Void> createRef(String installationToken,
                                  String repoFullName,
                                  String refName,
                                  String sha) {
        JsonObject body = new JsonObject().put("ref", refName).put("sha", sha);
        return tokenAuthedPost("/repos/" + repoFullName + "/git/refs", installationToken, body)
                .compose(resp -> {
                    int code = resp.statusCode();
                    if (code == 201) return Future.succeededFuture();
                    if (code == 422 && bodyMentions(resp, "Reference already exists")) {
                        return Future.succeededFuture();
                    }
                    return Future.failedFuture(httpError("createRef", resp));
                });
    }

    // ── HTTP plumbing ─────────────────────────────────────────────────────────

    private Future<HttpResponse<Buffer>> jwtAuthedGet(String path) {
        String jwt = jwtFactory.getAppJwt();
        return webClient.get(API_PORT, API_HOST, path)
                .ssl(true)
                .putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + jwt)
                .putHeader(HttpHeaders.ACCEPT.toString(), ACCEPT)
                .putHeader(API_VERSION_HEADER, API_VERSION)
                .send();
    }

    private Future<HttpResponse<Buffer>> jwtAuthedPost(String path, JsonObject body) {
        String jwt = jwtFactory.getAppJwt();
        return webClient.post(API_PORT, API_HOST, path)
                .ssl(true)
                .putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + jwt)
                .putHeader(HttpHeaders.ACCEPT.toString(), ACCEPT)
                .putHeader(API_VERSION_HEADER, API_VERSION)
                .sendJsonObject(body);
    }

    private Future<HttpResponse<Buffer>> tokenAuthedGet(String path, String token) {
        return webClient.get(API_PORT, API_HOST, path)
                .ssl(true)
                .putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + token)
                .putHeader(HttpHeaders.ACCEPT.toString(), ACCEPT)
                .putHeader(API_VERSION_HEADER, API_VERSION)
                .send();
    }

    private Future<HttpResponse<Buffer>> tokenAuthedPost(String path, String token, JsonObject body) {
        return webClient.post(API_PORT, API_HOST, path)
                .ssl(true)
                .putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + token)
                .putHeader(HttpHeaders.ACCEPT.toString(), ACCEPT)
                .putHeader(API_VERSION_HEADER, API_VERSION)
                .sendJsonObject(body);
    }

    private static Future<JsonObject> expectJsonObject(HttpResponse<Buffer> resp, String op) {
        if (resp.statusCode() / 100 != 2) {
            return Future.failedFuture(httpError(op, resp));
        }
        return Future.succeededFuture(resp.bodyAsJsonObject());
    }

    private static GitHubApiException httpError(String op, HttpResponse<Buffer> resp) {
        String body = resp.bodyAsString();
        return new GitHubApiException(op + " failed: HTTP " + resp.statusCode()
                + (body != null ? " — " + body : ""));
    }

    private static boolean bodyMentions(HttpResponse<Buffer> resp, String needle) {
        String body = resp.bodyAsString();
        return body != null && body.contains(needle);
    }

    /**
     * Token-cache key. Permissions ride as a {@code Map.of(...)} immutable map so that
     * {@link #READ_CONTENTS} and {@code Map.of("contents","read")} collide on the same
     * cache slot.
     */
    private record TokenKey(long installationId, Long repoId, Map<String, String> permissions) {}
}
