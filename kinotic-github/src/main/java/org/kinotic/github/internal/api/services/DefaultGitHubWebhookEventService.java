package org.kinotic.github.internal.api.services;

import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.github.api.model.GitHubWebhookEvent;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.api.services.GitHubWebhookEventService;
import org.kinotic.os.api.model.Project;
import org.kinotic.os.api.model.RepositoryConnectionStatus;
import org.kinotic.os.api.services.ProjectService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

/**
 * Default impl: mutates installation state for management events, flips backing
 * projects to {@link RepositoryConnectionStatus#DISCONNECTED} when GitHub revokes
 * access, and republishes a slim envelope for repo events on
 * {@code evt://github/<eventType>/<orgId>/<projectId>}.
 * <p>
 * Webhook deliveries have no Kinotic participant attached, so all reads and writes
 * go through the published {@link GitHubAppInstallationService} and
 * {@link ProjectService} wrapped in {@link SecurityContext#withElevatedAccess} —
 * the same single CRUD layer the rest of the platform uses, just with the per-call
 * org filter disabled.
 * <p>
 * Always succeeds — webhook handler returns 204 quickly and any internal error is
 * logged and dropped so GitHub does not redeliver.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGitHubWebhookEventService implements GitHubWebhookEventService {

    private static final String EVT_NAMESPACE = "github";

    private final SecurityContext securityContext;
    private final GitHubAppInstallationService installationService;
    private final ProjectService projectService;
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
        return securityContext.withElevatedAccess(
                () -> installationService.findByGithubInstallationId(installationId))
                .thenCompose(existing -> {
                    if (existing == null) {
                        // Created elsewhere or already removed — nothing to mutate.
                        return CompletableFuture.completedFuture(null);
                    }
                    return switch (action == null ? "" : action) {
                        case "deleted" -> securityContext.withElevatedAccess(
                                () -> installationService.deleteById(existing.getId()));
                        case "suspend" -> {
                            existing.setSuspendedAt(new Date()).setUpdated(new Date());
                            yield securityContext.withElevatedAccess(
                                    () -> installationService.save(existing)).thenApply(saved -> null);
                        }
                        case "unsuspend" -> {
                            existing.setSuspendedAt(null).setUpdated(new Date());
                            yield securityContext.withElevatedAccess(
                                    () -> installationService.save(existing)).thenApply(saved -> null);
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
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < removed.size(); i++) {
            String fullName = removed.getJsonObject(i).getString("full_name");
            if (fullName == null) continue;
            chain = chain.thenCompose(v -> markDisconnected(fullName));
        }
        return chain;
    }

    private CompletableFuture<Void> markDisconnected(String repoFullName) {
        return securityContext.withElevatedAccess(() -> projectService.findByRepoFullName(repoFullName))
                .thenCompose(projects -> {
                    if (projects.isEmpty()) {
                        log.debug("Installation lost access to {}; no Kinotic project backed by it", repoFullName);
                        return CompletableFuture.completedFuture(null);
                    }
                    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                    for (Project project : projects) {
                        if (project.getRepositoryConnectionStatus() == RepositoryConnectionStatus.DISCONNECTED) {
                            continue;
                        }
                        project.setRepositoryConnectionStatus(RepositoryConnectionStatus.DISCONNECTED);
                        log.warn("Flagging project {} (org {}) DISCONNECTED — installation lost access to {}",
                                 project.getId(), project.getOrganizationId(), repoFullName);
                        chain = chain.thenCompose(v -> securityContext
                                .withElevatedAccess(() -> projectService.save(project))
                                .thenApply(saved -> null));
                    }
                    return chain;
                });
    }

    private CompletableFuture<Void> handleRepoEvent(GitHubWebhookEvent event) {
        if (event.getRepoFullName() == null) {
            return CompletableFuture.completedFuture(null);
        }
        return securityContext.withElevatedAccess(
                () -> projectService.findByRepoFullName(event.getRepoFullName()))
                .thenAccept(projects -> {
                    if (projects.isEmpty()) {
                        log.debug("No Kinotic project for repo {} (event {}); dropping",
                                  event.getRepoFullName(), event.getEventType());
                        return;
                    }
                    for (Project project : projects) {
                        String cri = EventConstants.EVENT_DESTINATION_SCHEME + "://" + EVT_NAMESPACE + "/"
                                + event.getEventType()
                                + "/" + project.getOrganizationId() + "/" + project.getId();
                        byte[] payload = event.getPayload().encode().getBytes(StandardCharsets.UTF_8);
                        eventBusService.send(Event.create(cri, payload));
                    }
                });
    }
}
