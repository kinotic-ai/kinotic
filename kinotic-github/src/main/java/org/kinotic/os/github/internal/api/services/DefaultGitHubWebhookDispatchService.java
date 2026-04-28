package org.kinotic.os.github.internal.api.services;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.os.github.api.model.GitHubAppInstallation;
import org.kinotic.os.github.api.model.GitHubWebhookEnvelope;
import org.kinotic.os.github.api.model.ProjectGitHubRepoLink;
import org.kinotic.os.github.api.services.GitHubWebhookDispatchService;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Default impl: dedups by {@code X-GitHub-Delivery}, mutates installation state for
 * management events, looks up the project link for repo events, and republishes a
 * slim envelope on {@code evt://github/<eventType>/<orgId>/<projectId>}.
 * <p>
 * Talks to {@link CrudServiceTemplate} directly (rather than going through the
 * org-scoped CRUD services) because webhook dispatch is by definition cross-org —
 * the inbound delivery has no Kinotic participant attached.
 * <p>
 * Always succeeds — webhook handler returns 204 quickly and any internal error is
 * logged and dropped so GitHub does not redeliver.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGitHubWebhookDispatchService implements GitHubWebhookDispatchService {

    private static final String EVT_SCHEME = "evt";
    private static final String EVT_NAMESPACE = "github";
    private static final String INSTALLATION_INDEX = "kinotic_github_app_installation";
    private static final String LINK_INDEX = "kinotic_project_github_repo";

    private final CrudServiceTemplate crudServiceTemplate;
    private final EventBusService eventBusService;

    private final Cache<String, Boolean> deliveryDedup = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    @Override
    public CompletableFuture<Void> dispatch(GitHubWebhookEnvelope env) {
        // First-line dedup: GitHub redelivers on 5xx, and we want at-most-once dispatch.
        if (env.getDeliveryId() != null
                && deliveryDedup.asMap().putIfAbsent(env.getDeliveryId(), Boolean.TRUE) != null) {
            log.debug("Dropping duplicate GitHub delivery {}", env.getDeliveryId());
            return CompletableFuture.completedFuture(null);
        }
        try {
            return switch (env.getEventType()) {
                case "installation" -> handleInstallation(env);
                case "installation_repositories" -> handleInstallationRepos(env);
                default -> handleRepoEvent(env);
            };
        } catch (Exception e) {
            log.warn("Webhook dispatch failed for delivery {}: {}", env.getDeliveryId(), e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> handleInstallation(GitHubWebhookEnvelope env) {
        String action = env.getPayload().getString("action");
        JsonObject install = env.getPayload().getJsonObject("installation");
        String installationId = install != null
                ? String.valueOf(install.getLong("id"))
                : env.getInstallationId();
        if (installationId == null) {
            return CompletableFuture.completedFuture(null);
        }
        return findInstallationByGithubId(installationId).thenCompose(existing -> {
            if (existing == null) {
                // Created elsewhere or already removed — nothing to mutate.
                return CompletableFuture.completedFuture(null);
            }
            return switch (action == null ? "" : action) {
                case "deleted" -> crudServiceTemplate
                        .deleteById(INSTALLATION_INDEX, existing.getId(),
                                    b -> b.routing(existing.getOrganizationId()))
                        .thenApply(v -> null);
                case "suspend" -> {
                    existing.setSuspendedAt(new Date()).setUpdated(new Date());
                    yield crudServiceTemplate.save(INSTALLATION_INDEX, existing.getId(), existing,
                                                   b -> b.routing(existing.getOrganizationId()))
                                              .thenApply(v -> null);
                }
                case "unsuspend" -> {
                    existing.setSuspendedAt(null).setUpdated(new Date());
                    yield crudServiceTemplate.save(INSTALLATION_INDEX, existing.getId(), existing,
                                                   b -> b.routing(existing.getOrganizationId()))
                                              .thenApply(v -> null);
                }
                default -> CompletableFuture.completedFuture(null);
            };
        });
    }

    private CompletableFuture<Void> handleInstallationRepos(GitHubWebhookEnvelope env) {
        if (!"removed".equals(env.getPayload().getString("action"))) {
            return CompletableFuture.completedFuture(null);
        }
        var removed = env.getPayload().getJsonArray("repositories_removed");
        if (removed == null || removed.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < removed.size(); i++) {
            String fullName = removed.getJsonObject(i).getString("full_name");
            chain = chain.thenCompose(v -> findLinksByRepoFullName(fullName).thenCompose(links -> {
                CompletableFuture<Void> sub = CompletableFuture.completedFuture(null);
                for (ProjectGitHubRepoLink link : links) {
                    sub = sub.thenCompose(unused -> crudServiceTemplate
                            .deleteById(LINK_INDEX, link.getId(),
                                        b -> b.routing(link.getOrganizationId()))
                            .thenApply(r -> null));
                }
                return sub;
            }));
        }
        return chain;
    }

    private CompletableFuture<Void> handleRepoEvent(GitHubWebhookEnvelope env) {
        if (env.getRepoFullName() == null) {
            return CompletableFuture.completedFuture(null);
        }
        return findLinksByRepoFullName(env.getRepoFullName()).thenAccept(links -> {
            if (links.isEmpty()) {
                log.debug("No project link for repo {} (event {}); dropping",
                          env.getRepoFullName(), env.getEventType());
                return;
            }
            for (ProjectGitHubRepoLink link : links) {
                String cri = EVT_SCHEME + "://" + EVT_NAMESPACE + "/" + env.getEventType()
                        + "/" + link.getOrganizationId() + "/" + link.getProjectId();
                byte[] payload = env.getPayload().encode().getBytes(StandardCharsets.UTF_8);
                eventBusService.send(Event.create(cri, payload));
            }
        });
    }

    private CompletableFuture<GitHubAppInstallation> findInstallationByGithubId(String githubInstallationId) {
        Query q = Query.of(qb -> qb.bool(b -> b.filter(
                f -> f.term(t -> t.field("githubInstallationId").value(githubInstallationId)))));
        return crudServiceTemplate.search(INSTALLATION_INDEX,
                                          Pageable.ofSize(1),
                                          GitHubAppInstallation.class,
                                          b -> b.query(q))
                                  .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    private CompletableFuture<List<ProjectGitHubRepoLink>> findLinksByRepoFullName(String repoFullName) {
        Query q = Query.of(qb -> qb.bool(b -> b.filter(
                f -> f.term(t -> t.field("repoFullName").value(repoFullName)))));
        return crudServiceTemplate.search(LINK_INDEX,
                                          Pageable.ofSize(50),
                                          ProjectGitHubRepoLink.class,
                                          b -> b.query(q))
                                  .thenApply(page -> page.getContent());
    }
}
