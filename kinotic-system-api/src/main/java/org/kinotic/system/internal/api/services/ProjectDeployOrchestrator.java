package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.model.ProjectDeploymentStatus;
import org.kinotic.management.api.model.ProjectDeploymentStatusType;
import org.kinotic.management.api.model.grind.JobOwner;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.api.model.GitHubProjectEvent;
import org.kinotic.management.api.model.GitHubWebhookEvent;
import org.kinotic.management.api.services.github.GitHubProjectEventService;
import org.kinotic.system.api.model.grind.JobRunHandle;
import org.kinotic.system.api.services.JobService;
import org.kinotic.system.internal.api.model.deployment.PendingDeploy;
import org.kinotic.system.internal.api.model.deployment.ProjectDeployJob;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Deploys a project whenever a commit lands on its repository's default branch. Listens to
 * the verified GitHub deliveries {@link GitHubProjectEventService} emits, runs each
 * qualifying push as a grind job assembled by {@link ProjectDeployJobFactory},
 * and records the outcome on the project's {@link ProjectDeployment}. The project's own
 * repository has no CI — this job is it, so a commit whose build fails never reaches the
 * runtime workload.
 * <p>
 * Deployments are serialized per project with latest-wins: pushes arriving while a
 * deployment runs collapse to the newest commit, which deploys next — intermediate commits
 * are skipped, and GitHub redeliveries of the same commit are harmless because syncing a
 * commit twice converges to the same checkout.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDeployOrchestrator {

    /** The sha GitHub sends as {@code after} when a push deletes a ref. */
    private static final String ZERO_SHA = "0".repeat(40);

    private final GitHubProjectEventService gitHubProjectEventService;
    private final JobService jobService;
    private final ProjectDeployJobFactory jobFactory;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final ProjectRepository projectRepository;

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

    /**
     * Deploys the given commit of the project, completing when the deployment has finished
     * and its {@link ProjectDeployment} record reflects the outcome. Fails when the sync
     * workload's run fails — the workload is then kept so its logs can be inspected — or
     * when any other step of the deployment fails.
     *
     * @param organizationId the organization owning the project
     * @param projectId the project to deploy
     * @param commitSha the commit to bring the node's checkout to
     * @return a future that completes when the deployment has finished
     */
    public Future<Void> deployProject(String organizationId, String projectId, String commitSha) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notBlank(projectId, "projectId cannot be blank");
        Validate.notBlank(commitSha, "commitSha cannot be blank");
        return projectRepository.findById(projectId, organizationId)
                .compose(project -> {
                    Future<Void> ret;
                    if (project == null) {
                        ret = Future.failedFuture(new IllegalArgumentException("Project not found: " + projectId));
                    } else {
                        ret = projectDeploymentRepository.findById(projectId, organizationId)
                                                         .compose(existing -> runDeployJob(project, existing, commitSha));
                    }
                    return ret;
                });
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
        deployProject(organizationId, projectId, commitSha)
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

    private Future<Void> runDeployJob(Project project, ProjectDeployment existing, String commitSha) {
        ProjectDeployJob job = jobFactory.createJob(project, existing, commitSha);
        JobRunHandle execution = jobService.execute(job.getDefinition(),
                                                    JobOwner.ofApplication(project.getOrganizationId(),
                                                                           project.getApplicationId()));

        Promise<Void> outcome = Promise.promise();
        recordDeploying(project, existing, execution.getJobRunId())
                .onFailure(outcome::fail)
                // The job starts when its results are subscribed, so the DEPLOYING record
                // is in place before any step runs
                .onSuccess(deployment -> execution.getResults().subscribe(
                        result -> { },
                        error -> recordOutcome(deployment, job, null,
                                               new ProjectDeploymentStatus(ProjectDeploymentStatusType.FAILED,
                                                                           error.getMessage()))
                                .onComplete(unused -> outcome.fail(error)),
                        () -> recordOutcome(deployment, job, commitSha,
                                            new ProjectDeploymentStatus(ProjectDeploymentStatusType.RUNNING, null))
                                .<Void>mapEmpty()
                                .onComplete(outcome)));
        return outcome.future();
    }

    private Future<ProjectDeployment> recordDeploying(Project project, ProjectDeployment existing, String jobRunId) {
        ProjectDeployment deployment = existing != null ? existing : new ProjectDeployment()
                .setId(project.getId())
                .setOrganizationId(project.getOrganizationId())
                .setApplicationId(project.getApplicationId())
                .setCreated(new Date());
        deployment.setLastJobRunId(jobRunId);
        deployment.setStatus(new ProjectDeploymentStatus(ProjectDeploymentStatusType.DEPLOYING, null));
        deployment.setUpdated(new Date());
        return projectDeploymentRepository.save(deployment, deployment.getOrganizationId());
    }

    private Future<ProjectDeployment> recordOutcome(ProjectDeployment deployment,
                                                    ProjectDeployJob job,
                                                    String syncedCommitSha,
                                                    ProjectDeploymentStatus status) {
        if (job.getTarget() != null) {
            deployment.setNodeId(job.getTarget().nodeId());
            deployment.setHostDir(job.getTarget().hostDir());
        }
        if (job.getRuntimeWorkloadId() != null) {
            deployment.setRuntimeWorkloadId(job.getRuntimeWorkloadId());
        }
        if (syncedCommitSha != null) {
            deployment.setCommitSha(syncedCommitSha);
        }
        deployment.setStatus(status);
        deployment.setUpdated(new Date());
        return projectDeploymentRepository.save(deployment, deployment.getOrganizationId())
                .onFailure(error -> log.error("Failed to record deployment outcome for project {}",
                                              deployment.getId(), error));
    }

}
