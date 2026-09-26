package org.kinotic.system.internal.api.services.deployment;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.annotations.Consumer;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.services.Reconciler;
import org.kinotic.domain.api.model.Requeue;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.StatusConditions;
import org.kinotic.domain.api.model.WatchedParent;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.management.api.model.deployment.DeploymentState;
import org.kinotic.management.api.model.deployment.DeploymentStatusType;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.management.api.model.deployment.MicroserviceDeployment;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.deployment.ProjectPushEvent;
import org.kinotic.management.api.model.deployment.ProjectDeployment;
import org.kinotic.management.api.model.deployment.UiDeployment;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.api.repositories.ProjectSbomRepository;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.management.api.repositories.WorkloadRepository;
import org.kinotic.system.api.services.workload.WorkloadOrchestrationService;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.events.TaskCompletedEvent;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.management.api.model.deployment.DeployTarget;
import org.kinotic.system.api.model.deployment.ProjectDeployStores;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deploys a project whenever a commit lands on its repository's default branch, and keeps it
 * deployed. Consumes the verified {@link ProjectPushEvent}s the management module publishes to
 * the event fabric, so a push is recorded no matter which node received the webhook, as what the
 * project's {@link ProjectDeployment} should be: the commit, running. The reconcile master then
 * calls this worker, on one node, which runs each qualifying commit as a grind job created by
 * {@link ProjectDeployJobDefinitionFactory}, made by the record so a change to the run reaches this
 * worker, and reports the outcome on the record. The project's
 * own repository has no CI, this job is it, so a commit whose build fails never reaches the runtime
 * workloads.
 * <p>
 * Pushes arriving while a deployment runs collapse to the newest commit, which deploys next: the
 * record holds only the latest intent, and the master calls the worker once more when the run ends.
 * Intermediate commits are skipped, and GitHub redeliveries of the same commit are harmless because
 * an intent already in place is not written twice.
 * <p>
 * A deployment whose removal was asked for, which a project's deletion asks, is finalized bottom-up:
 * the removal of its microservice and UI deployments is asked for and their workers carry it out,
 * then the sync, publish and SBOM workloads are stopped, the sync machine removed, and the project's
 * SBOM record and the deployment's deleted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDeployOrchestrator implements Reconciler<ProjectDeployment> {

    /** How long between looks at children whose removal is under way. */
    private static final Duration CHILDREN_WAIT = Duration.ofSeconds(5);

    private final JobService jobService;
    private final ProjectDeployJobDefinitionFactory jobDefinitionFactory;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final ProjectSbomRepository projectSbomRepository;
    private final ProjectRepository projectRepository;
    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;
    private final UiDeploymentRepository uiDeploymentRepository;
    private final WorkloadRepository workloadRepository;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final ParticipantIdentityService participantIdentityService;
    private final JobRunRepository jobRunRepository;

    // The projects whose deployment job this node is running, so a record left DEPLOYING by a master
    // that died mid-run is told apart from one this master is running without a read of the run
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
    void onPush(ProjectPushEvent event) {
        deployProject(event.getOrganizationId(), event.getProjectId(), event.getCommitSha())
                .onFailure(error -> log.error("Could not record the push of {} to project {}",
                                              event.getCommitSha(), event.getProjectId(), error));
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
        boolean deploying = observed != null && observed.phase() == DeploymentStatusType.DEPLOYING;
        Future<Requeue> ret;
        if (state.getDeletionRequested() != null) {
            ret = finalizeRemoval(current);
        } else if (desired == null || desired.commitSha() == null) {
            ret = Future.succeededFuture(Requeue.NONE);
        } else if (intentSeen && !deploying) {
            // deployed, or failed and waiting for the next push: a build that failed is not retried
            ret = Future.succeededFuture(Requeue.NONE);
        } else if (deploying && running.contains(current.getId())) {
            ret = Future.succeededFuture(Requeue.NONE);
        } else {
            // a record left DEPLOYING by a run this node is not running is deployed again once that run
            // is known to be dead: the master that ran it is gone with its node
            Future<Boolean> abandoned = deploying ? runDead(current.getLastJobRunId()) : Future.succeededFuture(true);
            ret = abandoned.compose(dead -> {
                Future<Requeue> decided;
                if (!dead) {
                    decided = Future.succeededFuture(Requeue.NONE);
                } else {
                    decided = projectRepository.findById(current.getId(), current.getOrganizationId())
                            .compose(project -> project == null
                                    ? Future.succeededFuture(Requeue.NONE)
                                    : runDeployJob(project, current, desired.commitSha()).map(Requeue.NONE));
                }
                return decided;
            });
        }
        return ret;
    }

    // A run is dead when its record is gone or ended without the outcome reaching the deployment, or
    // when the node running it left the cluster; one still executing on a live node is left to finish
    private Future<Boolean> runDead(String jobRunId) {
        Future<Boolean> ret;
        if (jobRunId == null) {
            ret = Future.succeededFuture(true);
        } else {
            ret = jobRunRepository.findRun(jobRunId)
                                  .map(run -> run == null
                                          || run.getStatus() != ExecutionStatus.RUNNING
                                          || StatusConditions.has(run.getState().getConditions(), StatusConditionType.SERVER_NODE_LEFT));
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
                                                                    projectId),
                                             new WatchedParent(WatchedType.PROJECT_DEPLOYMENT, project.getOrganizationId(), projectId));
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
                                          new DeploymentState(DeploymentStatusType.FAILED, DeploymentState.commitOf(existing.getState().getObserved())),
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

    /**
     * Asks for the removal of every microservice and UI deployment of the project and waits for their
     * workers to finish, then stops the sync, publish and SBOM workloads, removes the sync machine and
     * deletes the project's SBOM record and the deployment's. What is already gone is not a failure.
     */
    private Future<Requeue> finalizeRemoval(ProjectDeployment current) {
        String projectId = current.getId();
        String source = "removal of project " + projectId;
        return Future.all(microserviceDeploymentRepository.findAllForProject(projectId),
                          uiDeploymentRepository.findAllForProject(projectId))
                .compose(found -> {
                    List<MicroserviceDeployment> microservices = found.resultAt(0);
                    List<UiDeployment> uis = found.resultAt(1);
                    Future<Requeue> ret;
                    if (microservices.isEmpty() && uis.isEmpty()) {
                        log.info("Removing the deployment of project {}: its children are gone", projectId);
                        ret = stop(current.getSyncWorkloadId())
                                .compose(v -> stop(current.getUiPublishWorkloadId()))
                                .compose(v -> stop(current.getSbomWorkloadId()))
                                .compose(v -> removeMachine(current.getSyncMachineIdentityId()))
                                // the machine the SBOM workload records as is gone, so no SBOM is recorded after this
                                .compose(v -> projectSbomRepository.deleteByIdSync(projectId, current.getOrganizationId()))
                                .compose(v -> projectDeploymentRepository.deleteByIdSync(projectId, current.getOrganizationId()))
                                .map(Requeue.NONE);
                    } else {
                        // asked once each; a child whose removal was already asked for keeps its request
                        Future<Void> asked = Future.succeededFuture();
                        for (MicroserviceDeployment microservice : microservices) {
                            asked = asked.compose(v -> microserviceDeploymentRepository.requestDeletion(microservice.getId(), source));
                        }
                        for (UiDeployment ui : uis) {
                            asked = asked.compose(v -> uiDeploymentRepository.requestDeletion(ui.getId(), source));
                        }
                        ret = asked.map(Requeue.after(CHILDREN_WAIT));
                    }
                    return ret;
                });
    }

    // The node removes what the ended run leaves, and the record stays; a record already deleted is
    // nothing to stop
    private Future<Void> stop(String workloadId) {
        Future<Void> ret;
        if (workloadId == null) {
            ret = Future.succeededFuture();
        } else {
            ret = workloadRepository.findById(workloadId)
                    .compose(workload -> workload != null
                            ? workloadOrchestrationService.stopWorkload(workloadId)
                            : Future.succeededFuture())
                    .recover(error -> {
                        log.warn("Workload {} could not be stopped: {}", workloadId, error.getMessage());
                        return Future.succeededFuture();
                    });
        }
        return ret;
    }

    // An org member may already have removed the machine from the console
    private Future<Void> removeMachine(String machineIdentityId) {
        Future<Void> ret;
        if (machineIdentityId == null) {
            ret = Future.succeededFuture();
        } else {
            ret = participantIdentityService.deleteById(machineIdentityId)
                    .recover(error -> {
                        log.warn("Machine {} could not be removed: {}", machineIdentityId, error.getMessage());
                        return Future.succeededFuture();
                    });
        }
        return ret;
    }

    // The run reports that it is deploying, which generation of intent it answers, and the commit
    // that stays live meanwhile
    private Future<Void> recordDeploying(ProjectDeployment deployment, String jobRunId, long generation) {
        String projectId = deployment.getId();
        String organizationId = deployment.getOrganizationId();
        return projectDeploymentRepository.recordJobRun(projectId, organizationId, jobRunId)
                .compose(v -> projectDeploymentRepository.reportObserved(projectId, organizationId,
                                                                        new DeploymentState(DeploymentStatusType.DEPLOYING, DeploymentState.commitOf(deployment.getState().getObserved())),
                                                                        generation, "deploy job " + jobRunId));
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
            ret = projectDeploymentRepository.recordTarget(projectId, organizationId, target);
        } else {
            ret = Future.succeededFuture();
        }
        return ret.compose(v -> projectDeploymentRepository.recordFailure(projectId, organizationId, failure))
                  .compose(v -> projectDeploymentRepository.reportObserved(projectId, organizationId, observed, generation,
                                                                           "deploy job " + jobRunId))
                  .onFailure(error -> log.error("Failed to record deployment outcome for project {}", projectId, error));
    }

}
