package org.kinotic.management.internal.api.services.gitea;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.management.internal.api.services.github.client.CreatedRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The calls the system makes to the Gitea server projects' repositories are hosted on, the {@code kinotic-gitea} of
 * {@code deployment/docker-compose/compose.gitea.yml}, through its {@code /api/v1} API as the administrator that
 * compose file creates, or as one of the accounts a repository is read and written through. Each {@code ensure}
 * call succeeds when the thing already exists, so a repeated or retried call is harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.managementApi.repoHost", havingValue = "GITEA")
public class GiteaClient {

    /**
     * Gitea as a person's browser and Git client reach it, its {@code ROOT_URL}: the base of a write token's clone URL
     * and of the portal's repository links.
     */
    static final String WEB_BASE_URL = "http://localhost:3001";

    // Gitea as the servers reach it on the compose network, and the administrator compose.gitea.yml creates
    private static final String API_URL = "http://kinotic-gitea:3000";
    private static final String ADMIN_USERNAME = "kinotic_admin";
    private static final String ADMIN_PASSWORD = "kinotic-gitea-admin";
    private static final String DEFAULT_BRANCH = "main";
    private static final String TOKEN_NAME_PREFIX = "kinotic-";
    private static final int USER_PAGE_SIZE = 50;

    private final Vertx vertx;
    private WebClient webClient;

    @PostConstruct
    public void start() {
        webClient = WebClient.create(vertx);
    }

    @PreDestroy
    public void stop() {
        if (webClient != null) {
            webClient.close();
        }
    }

    /** Creates the organization {@code name}, private, when it doesn't exist. */
    public Future<Void> ensureOrganization(String name) {
        JsonObject body = new JsonObject().put("username", name).put("visibility", "private");
        return send(asAdmin(HttpMethod.POST, "/orgs"), body).compose(created -> {
            Future<Void> ret;
            if (created.statusCode() == 422) {
                // Gitea answers 422 for an organization that exists and for a name it refuses, so the lookup decides
                ret = send(asAdmin(HttpMethod.GET, "/orgs/" + name), null)
                        .compose(found -> found.statusCode() == 200
                                ? Future.<Void>succeededFuture()
                                : expect(created, "create the organization " + name, 201));
            } else {
                ret = expect(created, "create the organization " + name, 201);
            }
            return ret;
        });
    }

    /** Creates the empty repository {@code owner/name}, whose default branch is created by its first commit. */
    public Future<CreatedRepository> createRepository(String owner, String name, String description, boolean isPrivate) {
        JsonObject body = new JsonObject().put("name", name)
                                          .put("private", isPrivate)
                                          .put("auto_init", false)
                                          .put("default_branch", DEFAULT_BRANCH);
        if (description != null && !description.isBlank()) {
            body.put("description", description);
        }
        return send(asAdmin(HttpMethod.POST, "/orgs/" + owner + "/repos"), body)
                .compose(response -> expect(response, "create the repository " + owner + "/" + name, 201)
                        .map(v -> {
                            JsonObject repo = response.bodyAsJsonObject();
                            return new CreatedRepository(repo.getLong("id"), repo.getString("full_name"),
                                                         repo.getString("default_branch"));
                        }));
    }

    /** Whether the repository has no commit yet. */
    public Future<Boolean> isRepositoryEmpty(String fullName) {
        return send(asAdmin(HttpMethod.GET, "/repos/" + fullName), null)
                .compose(response -> expect(response, "find the repository " + fullName, 200)
                        .map(v -> response.bodyAsJsonObject().getBoolean("empty", false)));
    }

    /** Commits {@code files}, Gitea's file operations, to {@code branch} of the repository in one commit. */
    public Future<Void> changeFiles(String fullName, String branch, String message, JsonArray files) {
        JsonObject body = new JsonObject().put("branch", branch).put("message", message).put("files", files);
        return send(asAdmin(HttpMethod.POST, "/repos/" + fullName + "/contents"), body)
                .compose(response -> expect(response, "commit to " + fullName, 201));
    }

    /** The commit {@code branch} of the repository points at, or null when the branch does not exist. */
    public Future<String> findBranchHead(String fullName, String branch) {
        return send(asAdmin(HttpMethod.GET, "/repos/" + fullName + "/branches/" + branch), null).compose(response -> {
            Future<String> ret;
            if (response.statusCode() == 404) {
                ret = Future.succeededFuture(null);
            } else {
                ret = expect(response, "read the branch " + branch + " of " + fullName, 200)
                        .map(v -> response.bodyAsJsonObject().getJsonObject("commit").getString("id"));
            }
            return ret;
        });
    }

    /** Gives the repository a push webhook delivering to {@code url}, signed with {@code secret}, when it has none. */
    public Future<Void> ensurePushWebhook(String fullName, String url, String secret) {
        return send(asAdmin(HttpMethod.GET, "/repos/" + fullName + "/hooks"), null).compose(found -> {
            Future<Void> ret;
            boolean exists = found.statusCode() == 200
                    && found.bodyAsJsonArray().stream()
                            .map(JsonObject.class::cast)
                            .anyMatch(hook -> url.equals(hook.getJsonObject("config", new JsonObject()).getString("url")));
            if (exists) {
                ret = Future.succeededFuture();
            } else {
                JsonObject body = new JsonObject()
                        .put("type", "gitea")
                        .put("active", true)
                        .put("events", new JsonArray().add("push"))
                        .put("config", new JsonObject().put("url", url).put("content_type", "json").put("secret", secret));
                ret = send(asAdmin(HttpMethod.POST, "/repos/" + fullName + "/hooks"), body)
                        .compose(response -> expect(response, "add the webhook of " + fullName, 201));
            }
            return ret;
        });
    }

    /**
     * Makes {@code username} a collaborator of the repository with {@code permission} ({@code read} or
     * {@code write}), creating the account with {@code password} first when it doesn't exist.
     */
    public Future<Void> ensureCollaborator(String fullName, String username, String password, String permission) {
        JsonObject account = new JsonObject().put("username", username)
                                              .put("email", username + "@kinotic.invalid")
                                              .put("password", password)
                                              .put("must_change_password", false)
                                              .put("visibility", "private");
        // 409: the account exists from an earlier credential of the same repository
        return send(asAdmin(HttpMethod.POST, "/admin/users"), account)
                .compose(created -> expect(created, "create the account " + username, 201, 409))
                .compose(v -> send(asAdmin(HttpMethod.PUT, "/repos/" + fullName + "/collaborators/" + username),
                                   new JsonObject().put("permission", permission)))
                .compose(response -> expect(response, "add " + username + " to " + fullName, 204));
    }

    /**
     * Creates an access token of {@code username} with the given scope, which is deleted once {@code lifetime} has
     * passed, and deletes the tokens this client created for the account more than {@code lifetime} ago.
     *
     * @return a future emitting the token
     */
    public Future<String> createToken(String username, String password, String scope, Duration lifetime) {
        long now = System.currentTimeMillis();
        String userUri = "/users/" + username + "/tokens";
        return deleteTokensCreatedBefore(username, password, now - lifetime.toMillis())
                .compose(v -> send(asUser(HttpMethod.POST, userUri, username, password),
                                   new JsonObject().put("name", TOKEN_NAME_PREFIX + now + "-" + UUID.randomUUID())
                                                   .put("scopes", new JsonArray().add(scope))))
                .compose(created -> expect(created, "create a token of " + username, 201)
                        .map(v -> {
                            JsonObject token = created.bodyAsJsonObject();
                            deleteAfter(lifetime, userUri + "/" + token.getLong("id"), username, password);
                            return token.getString("sha1");
                        }));
    }

    /** Deletes the tokens this client created for {@code username} before {@code instant}, in epoch milliseconds. */
    public Future<Void> deleteTokensCreatedBefore(String username, String password, long instant) {
        String userUri = "/users/" + username + "/tokens";
        return send(asUser(HttpMethod.GET, userUri, username, password), null)
                .compose(listed -> expect(listed, "list the tokens of " + username, 200).map(v -> listed.bodyAsJsonArray()))
                .compose(tokens -> Future.all(tokens.stream()
                                                    .map(JsonObject.class::cast)
                                                    .filter(token -> createdBefore(token.getString("name"), instant))
                                                    .map(token -> send(asUser(HttpMethod.DELETE, userUri + "/" + token.getLong("id"), username, password), null))
                                                    .toList()))
                .mapEmpty();
    }

    /** The login names of every account on the server. */
    public Future<List<String>> findAllUsernames() {
        return findUsernames(1, new ArrayList<>());
    }

    // Gitea pages the account list; an empty page ends it
    private Future<List<String>> findUsernames(int page, List<String> found) {
        return send(asAdmin(HttpMethod.GET, "/admin/users?limit=" + USER_PAGE_SIZE + "&page=" + page), null)
                .compose(response -> expect(response, "list the accounts", 200).map(v -> response.bodyAsJsonArray()))
                .compose(users -> {
                    users.stream().map(JsonObject.class::cast).forEach(user -> found.add(user.getString("login")));
                    return users.isEmpty() ? Future.succeededFuture(found) : findUsernames(page + 1, found);
                });
    }

    // Gitea tokens have no expiry of their own
    private void deleteAfter(Duration lifetime, String tokenUri, String username, String password) {
        vertx.timer(lifetime.toMillis(), TimeUnit.MILLISECONDS)
             .compose(v -> send(asUser(HttpMethod.DELETE, tokenUri, username, password), null))
             .onFailure(err -> log.warn("Deleting the Gitea token {} of {} failed", tokenUri, username, err));
    }

    // A token this client created is named kinotic-<creation time in milliseconds>-<uuid>
    private static boolean createdBefore(String name, long instant) {
        boolean ret = false;
        if (name != null && name.startsWith(TOKEN_NAME_PREFIX)) {
            String[] parts = name.substring(TOKEN_NAME_PREFIX.length()).split("-", 2);
            try {
                ret = Long.parseLong(parts[0]) < instant;
            } catch (NumberFormatException e) {
                // a token named by someone else stays
            }
        }
        return ret;
    }

    private HttpRequest<Buffer> asAdmin(HttpMethod method, String uri) {
        return asUser(method, uri, ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    private HttpRequest<Buffer> asUser(HttpMethod method, String uri, String username, String password) {
        return webClient.requestAbs(method, API_URL + "/api/v1" + uri)
                        .basicAuthentication(username, password);
    }

    private static Future<HttpResponse<Buffer>> send(HttpRequest<Buffer> request, JsonObject body) {
        return body != null ? request.sendJsonObject(body) : request.send();
    }

    private static Future<Void> expect(HttpResponse<Buffer> response, String operation, Integer... accepted) {
        Future<Void> ret;
        if (Set.of(accepted).contains(response.statusCode())) {
            ret = Future.succeededFuture();
        } else {
            ret = Future.failedFuture(new IllegalStateException("Gitea answered " + response.statusCode()
                                                                + " when asked to " + operation + ": " + response.bodyAsString()));
        }
        return ret;
    }

}
