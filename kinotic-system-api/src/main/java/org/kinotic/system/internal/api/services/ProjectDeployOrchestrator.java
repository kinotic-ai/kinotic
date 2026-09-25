package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.annotations.Consumer;
import org.kinotic.core.api.reconcile.ReconcileState;
import org.kinotic.core.api.reconcile.Reconciler;
import org.kinotic.core.api.reconcile.Requeue;
import org.kinotic.core.api.reconcile.WatchedType;
import org.kinotic.domain.api.model.DeploymentState;
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
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deploys a project whenever a commit lands on its repository's default branch, and keeps it
 * deployed. Consumes the verified {@link GitHubProjectEvent}s the management module publishes to
 * the event fabric, so a push is recorded no matter which node received the webhook, as what the
 * project's {@link ProjectDeployment} should be: the commit, running. The reconcile master then
 * calls this worker, on one node, which runs each qualifying commit as a grind job created by
 * {@link ProjectDeployJobDefinitionFactory} and reports the outcome on the record. The project's
 * own repository has no CI, this job is it, so a commit whose build fails never reaches the runtime
 * workloads.
 * <p>
 * Pushes arriving while a deployment runs collapse to the newest commit, which deploys next: the
 * record holds only the latest intent, and the master calls the worker once more when the run ends.
 * Intermediate commits are skipped, and GitHub redeliveries of the same commit are harmless because
 * an intent already in place is not written twice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDeployOrchestrator implements Reconciler<ProjectDeployment> {

    /** The sha GitHub sends as {@code after} when a push deletes a ref. */
    private static final String ZERO_SHA = "0".repeat(40);

    private final JobService jobService;
    private final ProjectDeployJobDefinitionFactory jobDefinitionFactory;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final ProjectRepository projectRepository;

    // The projects whose deployment job this node is running, so a record left DEPLOYING by a master
    // that died mid-run is told apart from one this master is running
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    /**
     * Records that the given commit of the project should be deployed. The deployment itself runs
     * once the reconcile master calls this worker for the record.
     *
     * @param organizationId the organization owning the project
     * @param projectId the project to deploy
     * @param commitSha the commit to bring the node's checkout to
     * @return a future that completes once the intent is recorded
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
                        ProjectDeployment upsert = new ProjectDeployment()
                                .setId(project.getId())
                                .setOrganizationId(project.getOrganizationId())
                                .setApplicationId(project.getApplicationId())
                                .setCreated(new Date())
                                .setUpdated(new Date());
                        ret = projectDeploymentRepository.updateDesired(projectId, organizationId,
                                                                        new DeploymentState(DeploymentStatusType.RUNNING, commitSha),
                                                                        upsert, "push of " + commitSha)
                                                         .mapEmpty();
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
                deployProject(event.getOrganizationId(), event.getProjectId(), commitSha)
                        .onFailure(error -> log.error("Could not record the push of {} to project {}",
                                                      commitSha, event.getProjectId(), error));
            }
        }
    }

    @Override
    public WatchedType type() {
        return WatchedType.PROJECT_DEPLOYMENT;
    }

    @Override
    public Future<Requeue> reconcile(ProjectDeployment current) {
        ReconcileState<DeploymentState> state = current.getState();
        DeploymentState desired = state.getDesired();
        DeploymentState observed = state.getObserved();
        boolean intentSeen = state.getObservedGeneration() >= state.getGeneration();
        // a run this node is not running left the record DEPLOYING: the master that ran it is gone
        boolean abandoned = observed != null && observed.phase() == DeploymentStatusType.DEPLOYING
                && !running.contains(current.getId());
        Future<Requeue> ret;
        if (desired == null || desired.commitSha() == null) {
            ret = Future.succeededFuture(Requeue.NONE);
        } else if (intentSeen && !abandoned) {
            // deployed, or failed and waiting for the next push: a build that failed is not retried
            ret = Future.succeededFuture(Requeue.NONE);
        } else {
            ret = projectRepository.findById(current.getId(), current.getOrganizationId())
                    .compose(project -> project == null
                            ? Future.succeededFuture(Requeue.NONE)
                            : runDeployJob(project, current, desired.commitSha()).map(Requeue.NONE));
        }
        return ret;
    }

    private Future<Void> runDeployJob(Project project, ProjectDeployment existing, String commitSha) {
        String projectId = project.getId();
        long generation = existing.getState().getGeneration();
        JobDefinition definition = jobDefinitionFactory.createJobDefinition(project, existing, commitSha);
        JobRunHandle handle = jobService.run(definition,
                                             JobOwner.ofApplication(project.getOrganizationId(),
                                                                    project.getApplicationId(),
                                                                    projectId));
        String jobRunId = handle.getJobRunId();
        log.debug("Deploying project {} at commit {} in job run {}", projectId, commitSha, jobRunId);
        running.add(projectId);

        // Captured from the run's TaskCompletedEvents as the task stores it in the job scope,
        // so the outcome record reflects how far the run got whatever the outcome
        AtomicReference<DeployTarget> target = new AtomicReference<>();

        Promise<Void> outcome = Promise.promise();
        recordDeploying(existing, jobRunId, generation)
                .onFailure(error -> {
                    running.remove(projectId);
                    outcome.fail(error);
                })
                // The job starts when its events are subscribed, so the DEPLOYING record
                // is in place before any task runs
                .onSuccess(unused -> handle.getEvents().subscribe(
                        event -> {
                            if (event instanceof TaskCompletedEvent completed) {
                                if (ProjectDeployStores.DEPLOY_TARGET.equals(completed.storedName())
                                        && completed.storedValue() instanceof DeployTarget resolved) {
                                    target.set(resolved);
                                }
                            }
                        },
                        error -> {
                            log.error("Deployment of project {} at commit {} failed", projectId, commitSha, error);
                            recordOutcome(existing, jobRunId, generation, target.get(),
                                          new DeploymentState(DeploymentStatusType.FAILED, liveCommit(existing)),
                                          error.getMessage())
                                    .onComplete(recorded -> {
                                        running.remove(projectId);
                                        // the run's failure is the record's, not the worker's: a retry waits for the next push
                                        outcome.complete();
                                    });
                        },
                        () -> {
                            log.debug("Deployed project {} at commit {}", projectId, commitSha);
                            recordOutcome(existing, jobRunId, generation, target.get(),
                                          new DeploymentState(DeploymentStatusType.RUNNING, commitSha), null)
                                    .onComplete(recorded -> {
                                        running.remove(projectId);
                                        outcome.handle(recorded);
                                    });
                        }));
        return outcome.future();
    }

    // The run reports that it is deploying, which generation of intent it answers, and the commit
    // that stays live meanwhile
    private Future<Void> recordDeploying(ProjectDeployment deployment, String jobRunId, long generation) {
        String projectId = deployment.getId();
        String organizationId = deployment.getOrganizationId();
        return projectDeploymentRepository.recordJobRun(projectId, organizationId, jobRunId)
                .compose(v -> projectDeploymentRepository.reportObserved(projectId, organizationId,
                                                                        new DeploymentState(DeploymentStatusType.DEPLOYING, liveCommit(deployment)),
                                                                        generation, "deploy job " + jobRunId));
    }

    // The commit the deployment serves as the record stood before this run: a failed run leaves it live
    private static String liveCommit(ProjectDeployment deployment) {
        DeploymentState observed = deployment.getState().getObserved();
        return observed != null ? observed.commitSha() : null;
    }

    // The run's own tasks write the record's entity fields as they go, so the outcome is written field
    // by field rather than from the copy captured before the job started
    private Future<Void> recordOutcome(ProjectDeployment deployment,
                                       String jobRunId,
                                       long generation,
                                       DeployTarget target,
                                       DeploymentState observed,
                                       String failure) {
        String projectId = deployment.getId();
        String organizationId = deployment.getOrganizationId();
        Future<Void> ret;
        if (target != null) {
            ret = projectDeploymentRepository.recordTarget(projectId, organizationId, target.nodeId(), target.hostDir(),
                                                           target.syncWorkloadId(), target.uiPublishWorkloadId());
        } else {
            ret = Future.succeededFuture();
        }
        return ret.compose(v -> projectDeploymentRepository.recordFailure(projectId, organizationId, failure))
                  .compose(v -> projectDeploymentRepository.reportObserved(projectId, organizationId, observed, generation,
                                                                           "deploy job " + jobRunId))
                  .onFailure(error -> log.error("Failed to record deployment outcome for project {}", projectId, error));
    }

}
