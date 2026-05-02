package org.kinotic.github.internal.api.services;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.model.GitHubWebhookEvent;
import org.kinotic.github.api.services.GitHubWebhookEventService;
import org.kinotic.os.api.model.Project;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Default impl: mutates installation state for management events, looks up the
 * backing project for repo events, and republishes a slim envelope on
 * {@code evt://github/<eventType>/<orgId>/<projectId>}.
 * <p>
 * Talks to {@link CrudServiceTemplate} directly (rather than going through the
 * org-scoped CRUD services) because webhook processing is by definition cross-org —
 * the inbound delivery has no Kinotic participant attached.
 * <p>
 * <strong>Idempotency is the consumer's responsibility.</strong> GitHub may redeliver
 * a webhook (5xx response, retry from the App's Advanced settings, etc.) and there is
 * no platform-side dedup — a per-node {@code X-GitHub-Delivery} cache wouldn't cover
 * cross-node redeliveries anyway, so we don't pretend to. Installation-state mutations
 * here are already idempotent (delete-on-already-gone, suspend-on-already-suspended);
 * downstream subscribers on the event bus must be too.
 * <p>
 * Always succeeds — webhook handler returns 204 quickly and any internal error is
 * logged and dropped so GitHub does not redeliver.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGitHubWebhookEventService implements GitHubWebhookEventService {

    private static final String EVT_NAMESPACE = "github";
    private static final String INSTALLATION_INDEX = "kinotic_github_app_installation";
    private static final String PROJECT_INDEX = "kinotic_project";

    private final CrudServiceTemplate crudServiceTemplate;
    private final EventBusService eventBusService;

    @Override
    public CompletableFuture<Void> process(GitHubWebhookEvent event) {
        try {
            return switch (event.getEventType()) {
                case "installation" -> handleInstallation(event);
                case "installation_repositories" -> handleInstallationRepos(event);
                default -> handleRepoEvent(event);
            };
        } catch (Exception e) {
            log.warn("Webhook processing failed for delivery {}: {}", event.getDeliveryId(), e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> handleInstallation(GitHubWebhookEvent event) {
        String action = event.getPayload().getString("action");
        JsonObject install = event.getPayload().getJsonObject("installation");
        Long installationId = install != null ? install.getLong("id") : event.getInstallationId();
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

    private CompletableFuture<Void> handleInstallationRepos(GitHubWebhookEvent event) {
        if (!"removed".equals(event.getPayload().getString("action"))) {
            return CompletableFuture.completedFuture(null);
        }
        var removed = event.getPayload().getJsonArray("repositories_removed");
        if (removed == null || removed.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        // Don't mutate Project rows from a webhook — auto-orphaning a project from its
        // backing repo is a destructive policy decision that belongs in an explicit flow.
        // Log and let the operator decide.
        for (int i = 0; i < removed.size(); i++) {
            String fullName = removed.getJsonObject(i).getString("full_name");
            log.warn("GitHub installation removed access to {}; the backing Kinotic Project (if any) "
                     + "is now orphaned from its repo and needs operator attention", fullName);
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleRepoEvent(GitHubWebhookEvent event) {
        if (event.getRepoFullName() == null) {
            return CompletableFuture.completedFuture(null);
        }
        return findProjectsByRepoFullName(event.getRepoFullName()).thenAccept(projects -> {
            if (projects.isEmpty()) {
                log.debug("No Kinotic project for repo {} (event {}); dropping",
                          event.getRepoFullName(), event.getEventType());
                return;
            }
            for (Project project : projects) {
                String cri = EventConstants.EVENT_DESTINATION_SCHEME + "://" + EVT_NAMESPACE + "/" + event.getEventType()
                        + "/" + project.getOrganizationId() + "/" + project.getId();
                byte[] payload = event.getPayload().encode().getBytes(StandardCharsets.UTF_8);
                eventBusService.send(Event.create(cri, payload));
            }
        });
    }

    private CompletableFuture<GitHubAppInstallation> findInstallationByGithubId(long githubInstallationId) {
        Query q = Query.of(qb -> qb.bool(b -> b.filter(
                f -> f.term(t -> t.field("githubInstallationId").value(githubInstallationId)))));
        return crudServiceTemplate.search(INSTALLATION_INDEX,
                                          Pageable.ofSize(1),
                                          GitHubAppInstallation.class,
                                          b -> b.query(q))
                                  .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    private CompletableFuture<List<Project>> findProjectsByRepoFullName(String repoFullName) {
        Query q = Query.of(qb -> qb.bool(b -> b.filter(
                f -> f.term(t -> t.field("repoFullName").value(repoFullName)))));
        return crudServiceTemplate.search(PROJECT_INDEX,
                                          Pageable.ofSize(50),
                                          Project.class,
                                          b -> b.query(q))
                                  .thenApply(page -> page.getContent());
    }
}
