package org.kinotic.github.internal.api.services.client;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Vert.x WebClient backed implementation of {@link GitHubApiClient}. Builds
 * authenticated requests (App JWT for install-token mint, install token for
 * everything else), parses responses into typed records, and backs the token
 * cache contract with an in-process Caffeine {@link AsyncLoadingCache}. Mirrors
 * the structure of {@code DefaultElasticVertxClient} in kinotic-persistence.
 */
@Slf4j
@Component
public class DefaultGitHubApiClient implements GitHubApiClient {

    private static final String ACCEPT = "application/vnd.github+json";
    private static final String API_HOST = "api.github.com";
    private static final int API_PORT = 443;
    private static final String API_VERSION = "2022-11-28";
    private static final String API_VERSION_HEADER = "X-GitHub-Api-Version";
    /**
     * Never return a token with less than this much life remaining; evict and reload
     * first. 10 minutes is comfortably above the slowest expected clone of a
     * multi-GB repo.
     */
    private static final Duration MIN_RETURNED_TOKEN_LIFETIME = Duration.ofMinutes(10);
    /** User-authorization token exchange lives on github.com, not api.github.com. */
    private static final String OAUTH_HOST = "github.com";
    private static final String USER_AGENT = "kinotic-platform";
    /** GitHub's per_page maximum for the /user/installations collection. */
    private static final int USER_INSTALLATIONS_PAGE_SIZE = 100;
    /**
     * Entry lifetime derived from the token GitHub actually issued rather than from an
     * assumption about its lifetime, so an entry is gone by the time it drops below
     * {@link #MIN_RETURNED_TOKEN_LIFETIME} and the loader mints a replacement.
     */
    private static final Expiry<TokenKey, GitHubToken> TOKEN_EXPIRY = new Expiry<>() {
        @Override
        public long expireAfterCreate(TokenKey key, GitHubToken token, long currentTime) {
            Duration usableLife = Duration.between(Instant.now().plus(MIN_RETURNED_TOKEN_LIFETIME),
                                                   token.getExpiresAt());
            return Math.max(0, usableLife.toNanos());
        }

        @Override
        public long expireAfterUpdate(TokenKey key, GitHubToken token, long currentTime, long currentDuration) {
            return expireAfterCreate(key, token, currentTime);
        }

        @Override
        public long expireAfterRead(TokenKey key, GitHubToken token, long currentTime, long currentDuration) {
            return currentDuration;
        }
    };
    private final GitHubAppJwtFactory jwtFactory;
    private final Vertx vertx;
    private AsyncLoadingCache<TokenKey, GitHubToken> tokenCache;
    private WebClient webClient;

    public DefaultGitHubApiClient(Vertx vertx, GitHubAppJwtFactory jwtFactory) {
        this.vertx = vertx;
        this.jwtFactory = jwtFactory;
    }

    @PostConstruct
    public void start() {
        this.webClient = WebClient.create(vertx, new WebClientOptions()
                .setSsl(true)
                .setUserAgent(USER_AGENT));
        this.tokenCache = Caffeine.newBuilder()
                                  .expireAfter(TOKEN_EXPIRY)
                                  .maximumSize(10_000)
                                  .buildAsync((TokenKey key, Executor _) ->
                                                      mintToken(key.installationId(), key.repoId(), key.permissions())
                                                              .toCompletionStage()
                                                              .toCompletableFuture());
    }

    @PreDestroy
    public void stop() {
        if (webClient != null) {
            webClient.close();
        }
    }

    @Override
    public Future<String> createBlob(String installationToken,
                                     String repoFullName,
                                     byte[] content) {
        JsonObject body = new JsonObject()
                .put("content", Base64.getEncoder().encodeToString(content))
                .put("encoding", "base64");
        return tokenAuthedPost("/repos/" + repoFullName + "/git/blobs", installationToken, body)
                .compose(resp -> {
                    if (resp.statusCode() == 201) {
                        return Future.succeededFuture(resp.bodyAsJsonObject().getString("sha"));
                    }
                    return Future.failedFuture(httpError("createBlob", resp));
                });
    }

    @Override
    public Future<String> createCommit(String installationToken,
                                       String repoFullName,
                                       String message,
                                       String treeSha) {
        // No "parents" key: GitHub creates a root commit.
        JsonObject body = new JsonObject()
                .put("message", message)
                .put("tree", treeSha);
        return tokenAuthedPost("/repos/" + repoFullName + "/git/commits", installationToken, body)
                .compose(resp -> {
                    if (resp.statusCode() == 201) {
                        return Future.succeededFuture(resp.bodyAsJsonObject().getString("sha"));
                    }
                    return Future.failedFuture(httpError("createCommit", resp));
                });
    }

    @Override
    public Future<Void> createRef(String installationToken,
                                  String repoFullName,
                                  String refName,
                                  String sha) {
        JsonObject body = new JsonObject().put("ref", refName).put("sha", sha);
        return tokenAuthedPost("/repos/" + repoFullName + "/git/refs", installationToken, body)
                .compose(resp -> {
                    int code = resp.statusCode();
                    if (code == 201) return Future.succeededFuture();
                    if (code == 422) {
                        String respBody = resp.bodyAsString();
                        if (respBody != null && respBody.contains("Reference already exists")) {
                            return Future.succeededFuture();
                        }
                    }
                    return Future.failedFuture(httpError("createRef", resp));
                });
    }

    @Override
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

    @Override
    public Future<String> createTree(String installationToken,
                                     String repoFullName,
                                     List<TreeEntry> entries) {
        JsonArray tree = new JsonArray();
        for (TreeEntry entry : entries) {
            JsonObject node = new JsonObject()
                    .put("path", entry.path())
                    .put("mode", entry.mode())
                    .put("type", "blob");
            if (entry.content() != null) {
                node.put("content", entry.content());
            } else {
                node.put("sha", entry.sha());
            }
            tree.add(node);
        }
        JsonObject body = new JsonObject().put("tree", tree);
        return tokenAuthedPost("/repos/" + repoFullName + "/git/trees", installationToken, body)
                .compose(resp -> {
                    if (resp.statusCode() == 201) {
                        return Future.succeededFuture(resp.bodyAsJsonObject().getString("sha"));
                    }
                    return Future.failedFuture(httpError("createTree", resp));
                });
    }

    @Override
    public Future<Buffer> downloadTarball(String installationToken,
                                          String repoFullName,
                                          String ref) {
        // The tarball endpoint 302s to codeload.github.com; followRedirects
        // handles the hop.
        return request(HttpMethod.GET, "/repos/" + repoFullName + "/tarball/" + ref, installationToken)
                        .followRedirects(true)
                        .send()
                        .compose(resp -> {
                            if (resp.statusCode() == 200) {
                                return Future.succeededFuture(resp.body());
                            }
                            return Future.failedFuture(httpError("downloadTarball", resp));
                        });
    }

    @Override
    public Future<String> exchangeUserAccessCode(String clientId, String clientSecret, String code) {
        JsonObject body = new JsonObject()
                .put("client_id", clientId)
                .put("client_secret", clientSecret)
                .put("code", code);
        return webClient.post(API_PORT, OAUTH_HOST, "/login/oauth/access_token")
                        .ssl(true)
                        .putHeader(HttpHeaders.ACCEPT.toString(), "application/json")
                        .sendJsonObject(body)
                        .compose(resp -> {
                            if (resp.statusCode() != 200) {
                                return Future.failedFuture(httpError("exchangeUserAccessCode", resp));
                            }
                            JsonObject json = resp.bodyAsJsonObject();
                            // github.com reports exchange failures (e.g. bad_verification_code)
                            // as a 200 with an "error" body, not a non-2xx status
                            String error = json.getString("error");
                            Future<String> ret;
                            if (error != null) {
                                ret = Future.failedFuture(new GitHubApiException(
                                        "exchangeUserAccessCode failed: " + error));
                            } else {
                                ret = Future.succeededFuture(json.getString("access_token"));
                            }
                            return ret;
                        });
    }

    @Override
    public Future<List<InstallationDetails>> listUserInstallations(String userAccessToken) {
        return fetchUserInstallationsPage(userAccessToken, 1, new ArrayList<>());
    }

    private Future<List<InstallationDetails>> fetchUserInstallationsPage(String userAccessToken,
                                                                         int page,
                                                                         List<InstallationDetails> collected) {
        return request(HttpMethod.GET,
                       "/user/installations?per_page=" + USER_INSTALLATIONS_PAGE_SIZE + "&page=" + page,
                       userAccessToken)
                        .send()
                        .compose(resp -> {
                            if (resp.statusCode() != 200) {
                                return Future.failedFuture(httpError("listUserInstallations", resp));
                            }
                            JsonArray installations = resp.bodyAsJsonObject()
                                                          .getJsonArray("installations", new JsonArray());
                            for (int i = 0; i < installations.size(); i++) {
                                JsonObject entry = installations.getJsonObject(i);
                                JsonObject account = entry.getJsonObject("account");
                                collected.add(new InstallationDetails(
                                        entry.getLong("id"),
                                        entry.getLong("app_id"),
                                        account != null ? account.getString("login") : null,
                                        account != null ? account.getString("type") : null));
                            }
                            Future<List<InstallationDetails>> ret;
                            if (installations.size() < USER_INSTALLATIONS_PAGE_SIZE) {
                                ret = Future.succeededFuture(collected);
                            } else {
                                ret = fetchUserInstallationsPage(userAccessToken, page + 1, collected);
                            }
                            return ret;
                        });
    }

    @Override
    public Future<GitHubToken> getToken(long installationId,
                                        Long repoId,
                                        Map<String, String> permissions) {
        return Future.fromCompletionStage(tokenCache.get(new TokenKey(installationId, repoId, permissions)));
    }

    private GitHubApiException httpError(String op, HttpResponse<Buffer> resp) {
        String body = resp.bodyAsString();
        return new GitHubApiException(op + " failed: HTTP " + resp.statusCode()
                                              + (body != null ? " — " + body : ""));
    }

    /**
     * Builds a request to the GitHub REST API carrying the standard Accept, API-version
     * and bearer headers. {@code bearer} is an App JWT for the install-token endpoints,
     * an installation token for repo operations, and a user access token for the
     * user-installations lookup.
     */
    private HttpRequest<Buffer> request(HttpMethod method, String path, String bearer) {
        return webClient.request(method, API_PORT, API_HOST, path)
                        .ssl(true)
                        .putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + bearer)
                        .putHeader(HttpHeaders.ACCEPT.toString(), ACCEPT)
                        .putHeader(API_VERSION_HEADER, API_VERSION);
    }

    private Future<GitHubToken> mintToken(long installationId,
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
        return request(HttpMethod.POST, "/app/installations/" + installationId + "/access_tokens",
                       jwtFactory.getAppJwt())
                .sendJsonObject(body)
                .compose(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        return Future.failedFuture(httpError("mintToken", resp));
                    }
                    JsonObject json = resp.bodyAsJsonObject();
                    return Future.succeededFuture(new GitHubToken(
                            json.getString("token"),
                            Instant.parse(json.getString("expires_at"))));
                });
    }

    @Override
    public Future<Void> updateRef(String installationToken,
                                  String repoFullName,
                                  String refName,
                                  String sha,
                                  boolean force) {
        JsonObject body = new JsonObject().put("sha", sha).put("force", force);
        return request(HttpMethod.PATCH, "/repos/" + repoFullName + "/git/refs/" + refName, installationToken)
                        .sendJsonObject(body)
                        .compose(resp -> {
                            if (resp.statusCode() == 200) {
                                return Future.succeededFuture();
                            }
                            return Future.failedFuture(httpError("updateRef", resp));
                        });
    }

    private Future<HttpResponse<Buffer>> tokenAuthedPost(String path, String token, JsonObject body) {
        return request(HttpMethod.POST, path, token).sendJsonObject(body);
    }

    /**
     * Token-cache key. Permissions ride as a {@code Map.of(...)} immutable map so that
     * {@link GitHubApiClient#READ_CONTENTS} and {@code Map.of("contents","read")}
     * collide on the same cache slot.
     */
    private record TokenKey(long installationId, Long repoId, Map<String, String> permissions) {
    }
}
