package org.kinotic.github.internal.api.services.client;

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
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Vert.x WebClient wrapper for {@code api.github.com}. The single concentration point
 * for HTTP: builds requests, attaches the right {@code Authorization: Bearer ...}
 * header (App JWT for install-token mint, install token for everything else), parses
 * responses, surfaces typed errors. Mirrors the structure of
 * {@code DefaultElasticVertxClient} in kinotic-persistence.
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

    private final Vertx vertx;
    private final GitHubAppJwtFactory jwtFactory;
    private WebClient webClient;

    public GitHubApiClient(Vertx vertx, GitHubAppJwtFactory jwtFactory) {
        this.vertx = vertx;
        this.jwtFactory = jwtFactory;
    }

    @PostConstruct
    public void start() {
        this.webClient = WebClient.create(vertx, new WebClientOptions()
                .setSsl(true)
                .setUserAgent(USER_AGENT));
    }

    @PreDestroy
    public void stop() {
        if (webClient != null) {
            webClient.close();
        }
    }

    // ── Installation lookup ───────────────────────────────────────────────────

    public Future<JsonObject> getInstallation(long installationId) {
        return jwtAuthedGet("/app/installations/" + installationId)
                .compose(resp -> expectJsonObject(resp, "getInstallation"));
    }

    /**
     * Mints a scoped installation access token. Restricting {@code repoId} +
     * {@code permissions} produces a token that cannot exceed the requested
     * permissions even if intercepted.
     */
    public Future<MintedToken> createInstallationToken(long installationId,
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
                    return Future.succeededFuture(new MintedToken(
                            json.getString("token"),
                            Instant.parse(json.getString("expires_at"))));
                });
    }

    // ── Repository creation from a template ───────────────────────────────────

    /**
     * Creates a new private repository under {@code owner} from the given template,
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
     * @return the created repo JSON ({@code id}, {@code full_name}, {@code default_branch}, ...)
     */
    public Future<JsonObject> createRepoFromTemplate(String installationToken,
                                                     String templateFullName,
                                                     String owner,
                                                     String name,
                                                     String description) {
        JsonObject body = new JsonObject()
                .put("owner", owner)
                .put("name", name)
                .put("include_all_branches", false)
                .put("private", true);
        if (description != null && !description.isBlank()) {
            body.put("description", description);
        }
        return tokenAuthedPost("/repos/" + templateFullName + "/generate", installationToken, body)
                .compose(resp -> {
                    if (resp.statusCode() == 201) {
                        return Future.succeededFuture(resp.bodyAsJsonObject());
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

    public record MintedToken(String token, Instant expiresAt) {}
}
