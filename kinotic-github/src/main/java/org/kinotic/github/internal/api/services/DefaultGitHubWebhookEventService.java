package org.kinotic.github.internal.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.github.api.model.GitHubWebhookEvent;
import org.kinotic.github.api.services.GitHubWebhookEventService;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.api.model.RepositoryConnectionStatus;
import org.kinotic.domain.internal.api.repositories.ProjectRepository;
import org.kinotic.github.internal.api.repositories.GitHubAppInstallationRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Default impl: mutates installation state for management events, flips backing
 * projects to {@link RepositoryConnectionStatus#DISCONNECTED} when GitHub revokes
 * access, and republishes a slim envelope for repo events on
 * {@code evt://github/<eventType>/<orgId>/<projectId>}.
 * <p>
 * Webhook deliveries have no Kinotic participant attached, so reads go through the
 * repositories' find-by-field finders (which need no org context, the search key is
 * globally unique) and writes call the org-scoped repository overloads directly with the
 * {@code organizationId} carried on the row just read.
 * <p>
 * Always succeeds — webhook handler returns 204 quickly and any internal error is
 * logged and dropped so GitHub does not redeliver.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGitHubWebhookEventService implements GitHubWebhookEventService {

    private static final String EVT_NAMESPACE = "github";

    private final GitHubAppInstallationRepository installationRepository;
    private final ProjectRepository projectRepository;
    private final EventBusService eventBusService;

    @Override
    public CompletableFuture<Void> process(GitHubWebhookEvent event) {
        CompletableFuture<Void> ret;
        try {
            ret = switch (event.getEventType()) {
                case "installation" -> handleInstallation(event);
                case "installation_repositories" -> handleInstallationRepos(event);
                default -> handleRepoEvent(event);
            };
        } catch (Exception e) {
            ret = CompletableFuture.failedFuture(e);
        }
        // Swallowing here rather than at each handler is what makes the "always succeeds"
        // contract hold for asynchronous failures too, not just synchronous throws.
        return ret.exceptionally(err -> {
            log.warn("Webhook processing failed for delivery {}: {}", event.getDeliveryId(), err.getMessage());
            return null;
        });
    }

    private CompletableFuture<Void> handleInstallation(GitHubWebhookEvent event) {
        String action = event.getPayload().getString("action");
        Long installationId = event.getInstallationId();
        if (installationId == null) {
            return CompletableFuture.completedFuture(null);
        }
        return installationRepository.findByGithubInstallationId(installationId)
                .thenCompose(existing -> {
                    if (existing == null) {
                        // Created elsewhere or already removed — nothing to mutate.
                        return CompletableFuture.completedFuture(null);
                    }
                    String orgId = existing.getOrganizationId();
                    return switch (action == null ? "" : action) {
                        case "deleted" -> installationRepository.deleteById(existing.getId(), orgId);
                        case "suspend" -> {
                            existing.setSuspendedAt(new Date()).setUpdated(new Date());
                            yield installationRepository.save(existing, orgId).thenApply(saved -> null);
                        }
                        case "unsuspend" -> {
                            existing.setSuspendedAt(null).setUpdated(new Date());
                            yield installationRepository.save(existing, orgId).thenApply(saved -> null);
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
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        for (int i = 0; i < removed.size(); i++) {
            String fullName = removed.getJsonObject(i).getString("full_name");
            if (fullName != null) {
                pending.add(markDisconnected(fullName));
            }
        }
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> markDisconnected(String repoFullName) {
        return projectRepository.findByRepoFullName(repoFullName)
                .thenCompose(projects -> {
                    if (projects.isEmpty()) {
                        log.debug("Installation lost access to {}; no Kinotic project backed by it", repoFullName);
                    }
                    List<CompletableFuture<Project>> saves = new ArrayList<>();
                    for (Project project : projects) {
                        if (project.getRepoConnectionStatus() == RepositoryConnectionStatus.DISCONNECTED) {
                            continue;
                        }
                        project.setRepoConnectionStatus(RepositoryConnectionStatus.DISCONNECTED);
                        log.warn("Flagging project {} (org {}) DISCONNECTED — installation lost access to {}",
                                 project.getId(), project.getOrganizationId(), repoFullName);
                        saves.add(projectRepository.save(project, project.getOrganizationId()));
                    }
                    return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
                });
    }

    private CompletableFuture<Void> handleRepoEvent(GitHubWebhookEvent event) {
        if (event.getRepoFullName() == null) {
            return CompletableFuture.completedFuture(null);
        }
        return projectRepository.findByRepoFullName(event.getRepoFullName())
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
