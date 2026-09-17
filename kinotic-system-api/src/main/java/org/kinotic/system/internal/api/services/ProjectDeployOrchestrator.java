package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.annotations.Consumer;
import org.kinotic.domain.api.model.DeploymentStatus;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.api.model.GitHubProjectEvent;
import org.kinotic.management.api.model.GitHubWebhookEvent;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.events.TaskCompletedEvent;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.system.api.model.deployment.DeployTarget;
import org.kinotic.system.api.model.deployment.ProjectDeployStores;
import org.kinotic.system.internal.api.model.deployment.PendingDeploy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deploys a project whenever a commit lands on its repository's default branch. Consumes
 * the verified {@link GitHubProjectEvent}s the management module publishes to the event
 * fabric — so a push deploys no matter which node received the webhook — runs each
 * qualifying push as a grind job created by {@link ProjectDeployJobDefinitionFactory},
 * and records the outcome on the project's {@link ProjectDeployment}. The project's own
 * repository has no CI — this job is it, so a commit whose build fails never reaches the
 * runtime workloads.
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

    private final JobService jobService;
    private final ProjectDeployJobDefinitionFactory jobDefinitionFactory;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final ProjectRepository projectRepository;

    private final Set<String> deployingProjects = new HashSet<>();
    private final Map<String, PendingDeploy> pendingDeploys = new HashMap<>();

    /**
     * Deploys the given commit of the project, completing when the deployment has finished
     * and its {@link ProjectDeployment} record reflects the outcome. Fails when the sync
     * workload's run fails — the workload is then kept so its logs can be inspected — or
     * when any other task of the deployment fails.
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

    @Consumer
    void onEvent(GitHubProjectEvent event) {
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
            log.debug("Project {} is already deploying; commit {} queued behind it", projectId, commitSha);
            pendingDeploys.put(projectId, new PendingDeploy(organizationId, commitSha));
        } else {
            deployingProjects.add(projectId);
            deployAndContinue(organizationId, projectId, commitSha);
        }
    }

    private void deployAndContinue(String organizationId, String projectId, String commitSha) {
        log.debug("Deploying project {} at commit {}", projectId, commitSha);
        deployProject(organizationId, projectId, commitSha)
                .onSuccess(unused -> log.debug("Deployed project {} at commit {}", projectId, commitSha))
                .onFailure(error -> log.error("Deployment of project {} at commit {} failed",
                                              projectId, commitSha, error))
                .onComplete(unused -> deployNextOrRelease(projectId));
    }

    private synchronized void deployNextOrRelease(String projectId) {
        PendingDeploy next = pendingDeploys.remove(projectId);
        if (next != null) {
            deployAndContinue(next.organizationId(), projectId, next.commitSha());
        } else {
            deployingProjects.remove(projectId);
        }
    }

    private Future<Void> runDeployJob(Project project, ProjectDeployment existing, String commitSha) {
        JobDefinition definition = jobDefinitionFactory.createJobDefinition(project, existing, commitSha);
        JobRunHandle handle = jobService.run(definition,
                                             JobOwner.ofApplication(project.getOrganizationId(),
                                                                    project.getApplicationId(),
                                                                    project.getId()));

        // Captured from the run's TaskCompletedEvents as the task stores it in the job scope,
        // so the outcome record reflects how far the run got whatever the outcome
        AtomicReference<DeployTarget> target = new AtomicReference<>();

        Promise<Void> outcome = Promise.promise();
        recordDeploying(project, existing, handle.getJobRunId())
                .onFailure(outcome::fail)
                // The job starts when its events are subscribed, so the DEPLOYING record
                // is in place before any task runs
                .onSuccess(deployment -> handle.getEvents().subscribe(
                        event -> {
                            if (event instanceof TaskCompletedEvent completed) {
                                if (ProjectDeployStores.DEPLOY_TARGET.equals(completed.storedName())
                                        && completed.storedValue() instanceof DeployTarget resolved) {
                                    target.set(resolved);
                                }
                            }
                        },
                        error -> recordOutcome(deployment, target.get(), null,
                                               new DeploymentStatus(DeploymentStatusType.FAILED,
                                                                           error.getMessage()))
                                .onComplete(unused -> outcome.fail(error)),
                        () -> recordOutcome(deployment, target.get(), commitSha,
                                            new DeploymentStatus(DeploymentStatusType.RUNNING, null))
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
        deployment.setStatus(new DeploymentStatus(DeploymentStatusType.DEPLOYING, null));
        deployment.setUpdated(new Date());
        return projectDeploymentRepository.save(deployment, deployment.getOrganizationId());
    }

    private Future<ProjectDeployment> recordOutcome(ProjectDeployment deployment,
                                                    DeployTarget target,
                                                    String syncedCommitSha,
                                                    DeploymentStatus status) {
        // The run's own tasks write to this record — provisioning the sync machine records its
        // id before handing the credential out, the sync workload reports the artifacts — so the
        // copy captured before the job started is stale by now and writing it back would drop
        // what they wrote.
        return projectDeploymentRepository.findById(deployment.getId(), deployment.getOrganizationId())
                .map(current -> current != null ? current : deployment)
                .compose(current -> {
                    if (target != null) {
                        current.setNodeId(target.nodeId());
                        current.setHostDir(target.hostDir());
                        current.setSyncWorkloadId(target.syncWorkloadId());
                        current.setUiPublishWorkloadId(target.uiPublishWorkloadId());
                    }
                    if (syncedCommitSha != null) {
                        current.setCommitSha(syncedCommitSha);
                    }
                    current.setStatus(status);
                    current.setUpdated(new Date());
                    return projectDeploymentRepository.save(current, current.getOrganizationId());
                })
                .onFailure(error -> log.error("Failed to record deployment outcome for project {}",
                                              deployment.getId(), error));
    }

}
