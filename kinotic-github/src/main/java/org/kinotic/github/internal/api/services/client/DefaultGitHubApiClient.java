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
    private static final String USER_AGENT = "kinotic-platform";
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
        // expireAfterWrite matches GitHub's 60-minute issued lifetime; the per-read
        // freshness check below evicts entries earlier when they're about to die.
        this.tokenCache = Caffeine.newBuilder()
                                  .expireAfterWrite(Duration.ofMinutes(50))
                                  .maximumSize(10_000)
                                  .buildAsync((TokenKey key, java.util.concurrent.Executor _) ->
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
    public Future<InstallationDetails> getInstallation(long installationId) {
        return jwtAuthedGet("/app/installations/" + installationId)
                .compose(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        return Future.<JsonObject>failedFuture(httpError("getInstallation", resp));
                    }
                    return Future.succeededFuture(resp.bodyAsJsonObject());
                })
                .map(json -> {
                    JsonObject account = json.getJsonObject("account");
                    return new InstallationDetails(
                            json.getLong("id"),
                            account != null ? account.getString("login") : null,
                            account != null ? account.getString("type") : null);
                });
    }

    @Override
    public Future<GitHubToken> getToken(long installationId,
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


    private boolean hasEnoughLife(GitHubToken token) {
        return token.getExpiresAt().isAfter(Instant.now().plus(MIN_RETURNED_TOKEN_LIFETIME));
    }

    private GitHubApiException httpError(String op, HttpResponse<Buffer> resp) {
        String body = resp.bodyAsString();
        return new GitHubApiException(op + " failed: HTTP " + resp.statusCode()
                                              + (body != null ? " — " + body : ""));
    }

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
        return jwtAuthedPost("/app/installations/" + installationId + "/access_tokens", body)
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

    private Future<HttpResponse<Buffer>> tokenAuthedPost(String path, String token, JsonObject body) {
        return webClient.post(API_PORT, API_HOST, path)
                        .ssl(true)
                        .putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + token)
                        .putHeader(HttpHeaders.ACCEPT.toString(), ACCEPT)
                        .putHeader(API_VERSION_HEADER, API_VERSION)
                        .sendJsonObject(body);
    }

    /**
     * Token-cache key. Permissions ride as a {@code Map.of(...)} immutable map so that
     * {@link GitHubApiClient#READ_CONTENTS} and {@code Map.of("contents","read")}
     * collide on the same cache slot.
     */
    private record TokenKey(long installationId, Long repoId, Map<String, String> permissions) {
    }
}
