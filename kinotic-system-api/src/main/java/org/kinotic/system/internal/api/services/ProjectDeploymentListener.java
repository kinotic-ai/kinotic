package org.kinotic.system.internal.api.services;

import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.model.GitHubProjectEvent;
import org.kinotic.os.api.model.GitHubWebhookEvent;
import org.kinotic.os.api.services.GitHubProjectEventService;
import org.kinotic.system.internal.api.model.deployment.PendingDeploy;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Deploys a project whenever a commit lands on its repository's default branch. Listens to
 * the verified GitHub deliveries {@link GitHubProjectEventService} emits, and hands each
 * qualifying push to {@link ProjectDeployService}.
 * <p>
 * Deployments are serialized per project with latest-wins: pushes arriving while a
 * deployment runs collapse to the newest commit, which deploys next — intermediate commits
 * are skipped, and GitHub redeliveries of the same commit are harmless because syncing a
 * commit twice converges to the same checkout.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDeploymentListener {

    /** The sha GitHub sends as {@code after} when a push deletes a ref. */
    private static final String ZERO_SHA = "0".repeat(40);

    private final GitHubProjectEventService gitHubProjectEventService;
    private final ProjectDeployService projectDeployService;

    private final Set<String> deployingProjects = new HashSet<>();
    private final Map<String, PendingDeploy> pendingDeploys = new HashMap<>();

    private Disposable subscription;

    @PostConstruct
    void start() {
        // The event stream is best-effort and hot: an error tears it down, so resubscribe
        // with backoff instead of staying deaf to pushes
        subscription = gitHubProjectEventService.events()
                .doOnError(error -> log.warn("GitHub project event stream failed, resubscribing", error))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                                .maxBackoff(Duration.ofSeconds(30)))
                .subscribe(this::onEvent,
                           error -> log.error("GitHub project event stream terminated", error));
    }

    @PreDestroy
    void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }

    private void onEvent(GitHubProjectEvent event) {
        GitHubWebhookEvent webhook = event.getWebhookEvent();
        if ("push".equals(webhook.getEventType())) {
            JsonObject payload = webhook.getPayload();
            String commitSha = payload.getString("after");
            String defaultBranch = payload.getJsonObject("repository") != null
                    ? payload.getJsonObject("repository").getString("default_branch")
                    : null;
            boolean deploys = !payload.getBoolean("deleted", false)
                    && commitSha != null && !ZERO_SHA.equals(commitSha)
                    && defaultBranch != null
                    && ("refs/heads/" + defaultBranch).equals(payload.getString("ref"));
            if (deploys) {
                enqueue(event.getOrganizationId(), event.getProjectId(), commitSha);
            }
        }
    }

    private synchronized void enqueue(String organizationId, String projectId, String commitSha) {
        if (deployingProjects.contains(projectId)) {
            // latest-wins: only the newest commit waits, older queued ones are superseded
            pendingDeploys.put(projectId, new PendingDeploy(organizationId, commitSha));
        } else {
            deployingProjects.add(projectId);
            deploy(organizationId, projectId, commitSha);
        }
    }

    private void deploy(String organizationId, String projectId, String commitSha) {
        log.info("Deploying project {} at commit {}", projectId, commitSha);
        projectDeployService.deployProject(organizationId, projectId, commitSha)
                .onSuccess(unused -> log.info("Deployed project {} at commit {}", projectId, commitSha))
                .onFailure(error -> log.error("Deployment of project {} at commit {} failed",
                                              projectId, commitSha, error))
                .onComplete(unused -> deployNextOrRelease(projectId));
    }

    private synchronized void deployNextOrRelease(String projectId) {
        PendingDeploy next = pendingDeploys.remove(projectId);
        if (next != null) {
            deploy(next.organizationId(), projectId, next.commitSha());
        } else {
            deployingProjects.remove(projectId);
        }
    }

}
