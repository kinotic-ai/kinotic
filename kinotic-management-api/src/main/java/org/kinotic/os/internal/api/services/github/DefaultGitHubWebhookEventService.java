package org.kinotic.os.internal.api.services.github;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.model.GitHubProjectEvent;
import org.kinotic.os.api.model.GitHubWebhookEvent;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.api.model.RepositoryConnectionStatus;
import org.kinotic.domain.internal.api.repositories.ProjectRepository;
import org.kinotic.domain.internal.api.repositories.GitHubAppInstallationRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Default impl: mutates installation state for management events, flips backing
 * projects to {@link RepositoryConnectionStatus#DISCONNECTED} when GitHub revokes
 * access, and emits a {@link GitHubProjectEvent} per backing project for repo events.
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

    private final GitHubAppInstallationRepository installationRepository;
    private final ProjectRepository projectRepository;
    private final Vertx vertx;

    // Hot sink shared by every events() subscriber; never terminates. Best-effort delivery keeps a
    // slow subscriber from stalling the webhook handler, matching GitHub's at-most-once semantics.
    private final Sinks.Many<GitHubProjectEvent> sink = Sinks.many().multicast().directBestEffort();

    // One shared context every emission is delivered on: it serializes concurrent deliveries (Sinks
    // reject concurrent emission) and keeps subscriber chains off the threads that complete the
    // project lookup.
    private Context deliveryContext;

    @PostConstruct
    void init() {
        deliveryContext = vertx.getOrCreateContext();
    }

    @Override
    public Flux<GitHubProjectEvent> events() {
        return sink.asFlux();
    }

    @Override
    public Future<Void> process(GitHubWebhookEvent event) {
        Future<Void> ret;
        try {
            ret = switch (event.getEventType()) {
                case "installation" -> handleInstallation(event);
                case "installation_repositories" -> handleInstallationRepos(event);
                default -> handleRepoEvent(event);
            };
        } catch (Exception e) {
            ret = Future.failedFuture(e);
        }
        // Swallowing here rather than at each handler is what makes the "always succeeds"
        // contract hold for asynchronous failures too, not just synchronous throws.
        return ret.otherwise(err -> {
            log.warn("Webhook processing failed for delivery {}: {}", event.getDeliveryId(), err.getMessage());
            return null;
        });
    }

    private Future<Void> handleInstallation(GitHubWebhookEvent event) {
        String action = event.getPayload().getString("action");
        Long installationId = event.getInstallationId();
        if (installationId == null) {
            return Future.succeededFuture();
        }
        return installationRepository.findByGithubInstallationId(installationId)
                .compose(existing -> {
                    if (existing == null) {
                        // Created elsewhere or already removed — nothing to mutate.
                        return Future.succeededFuture();
                    }
                    String orgId = existing.getOrganizationId();
                    return switch (action == null ? "" : action) {
                        case "deleted" -> installationRepository.deleteById(existing.getId(), orgId);
                        case "suspend" -> {
                            existing.setSuspendedAt(new Date()).setUpdated(new Date());
                            yield installationRepository.save(existing, orgId).mapEmpty();
                        }
                        case "unsuspend" -> {
                            existing.setSuspendedAt(null).setUpdated(new Date());
                            yield installationRepository.save(existing, orgId).mapEmpty();
                        }
                        default -> Future.succeededFuture();
                    };
                });
    }

    private Future<Void> handleInstallationRepos(GitHubWebhookEvent event) {
        if (!"removed".equals(event.getPayload().getString("action"))) {
            return Future.succeededFuture();
        }
        var removed = event.getPayload().getJsonArray("repositories_removed");
        if (removed == null || removed.isEmpty()) {
            return Future.succeededFuture();
        }
        List<Future<Void>> pending = new ArrayList<>();
        for (int i = 0; i < removed.size(); i++) {
            String fullName = removed.getJsonObject(i).getString("full_name");
            if (fullName != null) {
                pending.add(markDisconnected(fullName));
            }
        }
        return Future.all(pending).mapEmpty();
    }

    private Future<Void> markDisconnected(String repoFullName) {
        return projectRepository.findByRepoFullName(repoFullName)
                .compose(projects -> {
                    if (projects.isEmpty()) {
                        log.debug("Installation lost access to {}; no Kinotic project backed by it", repoFullName);
                    }
                    List<Future<Project>> saves = new ArrayList<>();
                    for (Project project : projects) {
                        if (project.getRepoConnectionStatus() == RepositoryConnectionStatus.DISCONNECTED) {
                            continue;
                        }
                        project.setRepoConnectionStatus(RepositoryConnectionStatus.DISCONNECTED);
                        log.warn("Flagging project {} (org {}) DISCONNECTED — installation lost access to {}",
                                 project.getId(), project.getOrganizationId(), repoFullName);
                        saves.add(projectRepository.save(project, project.getOrganizationId()));
                    }
                    return Future.all(saves).mapEmpty();
                });
    }

    private Future<Void> handleRepoEvent(GitHubWebhookEvent event) {
        if (event.getRepoFullName() == null) {
            return Future.succeededFuture();
        }
        return projectRepository.findByRepoFullName(event.getRepoFullName())
                .compose(projects -> {
                    if (projects.isEmpty()) {
                        log.debug("No Kinotic project for repo {} (event {}); dropping",
                                  event.getRepoFullName(), event.getEventType());
                    } else {
                        for (Project project : projects) {
                            emit(new GitHubProjectEvent().setOrganizationId(project.getOrganizationId())
                                                         .setProjectId(project.getId())
                                                         .setWebhookEvent(event));
                        }
                    }
                    return Future.succeededFuture();
                });
    }

    private void emit(GitHubProjectEvent event) {
        deliveryContext.runOnContext(v -> {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                log.warn("Failed to emit {} event for project {}: {}",
                         event.getWebhookEvent().getEventType(), event.getProjectId(), result);
            }
        });
    }
}
