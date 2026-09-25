package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.reconcile.ReconcileState;
import org.kinotic.domain.api.reconcile.Reconciler;
import org.kinotic.domain.api.reconcile.Requeue;
import org.kinotic.domain.api.reconcile.StatusCondition;
import org.kinotic.domain.api.reconcile.StatusConditionType;
import org.kinotic.domain.api.reconcile.StatusConditions;
import org.kinotic.domain.api.reconcile.WatchedType;
import org.kinotic.domain.api.model.DeploymentState;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.management.api.model.MicroserviceArtifact;
import org.kinotic.management.api.model.MicroserviceDeployment;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.system.api.services.WorkloadOrchestrationService;
import org.kinotic.system.api.services.WorkloadService;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Keeps each microservice of a deployed project running as its deployment says it should: the worker
 * the reconcile master calls for a {@link MicroserviceDeployment} when its intent changes, when its
 * workload changes, and when it is found not in its desired state. A running VM that starts the
 * module the commit names is kept, and picks the commit up through the reload sentinel the sync
 * workload wrote; one whose module moved is stopped and replaced; one whose run ended is replaced,
 * after a wait when it failed, so a crashing service restarts at this worker's pace; one on a node
 * the platform cannot reach is left alone, since it may well be running, and the deployment says so
 * beside what it reports. A deployment for which the commit dropped the microservice is left as it
 * is, and one whose removal was asked for is finalized: its VM stopped, its machine identity removed,
 * its record deleted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MicroserviceDeployOrchestrator implements Reconciler<MicroserviceDeployment> {

    /** How long a VM that failed waits before it is started again. */
    static final Duration RESTART_DELAY = Duration.ofSeconds(30);

    // The deploy job records the commit's artifacts before it writes the intents; an intent ahead of
    // its artifacts is looked at again once the job has caught up
    private static final Duration ARTIFACTS_WAIT = Duration.ofSeconds(10);

    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final WorkloadService workloadService;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final ProjectDeployIdentityService projectDeployIdentityService;
    private final ProjectWorkloadFactory projectWorkloadFactory;
    private final ParticipantIdentityService participantIdentityService;

    @Override
    public WatchedType type() {
        return WatchedType.MICROSERVICE_DEPLOYMENT;
    }

    @Override
    public Future<Requeue> reconcile(MicroserviceDeployment current) {
        ReconcileState<DeploymentState> state = current.getState();
        DeploymentState desired = state.getDesired();
        Future<Requeue> ret;
        if (state.getDeletionRequested() != null) {
            ret = finalizeRemoval(current);
        } else if (desired == null) {
            ret = Future.succeededFuture(Requeue.NONE);
        } else if (desired.phase() == DeploymentStatusType.ORPHANED) {
            // the commit dropped the microservice: whatever runs keeps running until the deployment is removed
            ret = microserviceDeploymentRepository.reportObserved(current.getId(), desired, state.getGeneration(), "orphaned by the commit")
                                                  .map(Requeue.NONE);
        } else {
            ret = Future.all(workloadOf(current),
                             projectDeploymentRepository.findById(current.getProjectId(), current.getOrganizationId()))
                        .compose(found -> ensureRunning(current, desired, found.resultAt(0), found.resultAt(1)));
        }
        return ret;
    }

    private Future<Workload> workloadOf(MicroserviceDeployment deployment) {
        return deployment.getWorkloadId() == null
                ? Future.succeededFuture()
                : workloadService.findById(deployment.getWorkloadId());
    }

    private Future<Requeue> ensureRunning(MicroserviceDeployment current, DeploymentState desired, Workload workload, ProjectDeployment target) {
        Future<Requeue> ret;
        StatusCondition unreachable = workload == null ? null
                : StatusConditions.find(workload.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE).orElse(null);
        if (unreachable != null) {
            // the VM may well be running on the far side of a partition, so nothing is deployed beside
            // it; the node's next report, or its deregistration, brings the record back here
            ret = microserviceDeploymentRepository.setCondition(current.getId(), unreachable, "workload " + workload.getId())
                                                  .map(Requeue.NONE);
        } else if (StatusConditions.has(current.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE)) {
            ret = microserviceDeploymentRepository.clearCondition(current.getId(), StatusConditionType.NODE_UNREACHABLE, "workload reachable")
                                                  .compose(cleared -> decide(current, desired, workload, target));
        } else {
            ret = decide(current, desired, workload, target);
        }
        return ret;
    }

    private Future<Requeue> decide(MicroserviceDeployment current, DeploymentState desired, Workload workload, ProjectDeployment target) {
        ReconcileState<DeploymentState> state = current.getState();
        boolean intentSeen = state.getObservedGeneration() >= state.getGeneration();
        boolean open = workload != null && workload.getStatus().isOpen();
        Future<Requeue> ret;
        if (open && intentSeen) {
            // the running VM answered this intent already; a change to its run brings the record back here
            ret = Future.succeededFuture(Requeue.NONE);
        } else if (target == null || target.getNodeId() == null) {
            ret = failIntent(current, desired, "Project " + current.getProjectId() + " has no deployment target; deploy the project first");
        } else if (!desired.commitSha().equals(target.getArtifactsCommitSha())) {
            ret = Future.succeededFuture(Requeue.after(ARTIFACTS_WAIT));
        } else {
            MicroserviceArtifact artifact = target.getArtifacts().microservices().stream()
                                                  .filter(candidate -> candidate.name().equals(current.getName()))
                                                  .findFirst()
                                                  .orElse(null);
            if (artifact == null) {
                ret = failIntent(current, desired, "Commit " + desired.commitSha() + " has no microservice " + current.getName());
            } else {
                ret = ensureWorkload(current, desired, workload, target, artifact.dir() + "/" + artifact.entry());
            }
        }
        return ret;
    }

    private Future<Requeue> ensureWorkload(MicroserviceDeployment current, DeploymentState desired, Workload workload,
                                           ProjectDeployment target, String entryPoint) {
        ReconcileState<DeploymentState> state = current.getState();
        boolean intentSeen = state.getObservedGeneration() >= state.getGeneration();
        boolean open = workload != null && workload.getStatus().isOpen();
        boolean lastAnswerFailed = state.getObserved() != null && state.getObserved().phase() == DeploymentStatusType.FAILED;
        Future<Requeue> ret;
        if (open && entryPoint.equals(current.getEntryPoint())) {
            // the running supervisor picks the new commit up through the reload sentinel the sync workload wrote
            ret = answered(current, desired, new DeploymentState(DeploymentStatusType.DEPLOYED, desired.commitSha()), null);
        } else if (open) {
            ret = workloadOrchestrationService.stopWorkload(workload.getId())
                    .recover(error -> {
                        log.warn("Runtime workload {} of microservice {} of project {} could not be stopped: {}",
                                 workload.getId(), current.getName(), current.getProjectId(), error.getMessage());
                        return Future.succeededFuture();
                    })
                    .compose(v -> deploy(current, desired, target, entryPoint));
        } else if (intentSeen && lastAnswerFailed) {
            // the deployment this intent asked for failed; the next push, or a restart from the console, renews it
            ret = Future.succeededFuture(Requeue.NONE);
        } else if (workload != null && workload.getStatus() == WorkloadStatus.FAILED && sinceEnded(workload).compareTo(RESTART_DELAY) < 0) {
            ret = microserviceDeploymentRepository.recordFailure(current.getId(), runEnded(workload) + "; it is started again shortly")
                                                  .map(Requeue.after(RESTART_DELAY.minus(sinceEnded(workload))));
        } else {
            ret = deploy(current, desired, target, entryPoint);
        }
        return ret;
    }

    private static Duration sinceEnded(Workload workload) {
        return workload.getUpdated() == null ? RESTART_DELAY : Duration.ofMillis(System.currentTimeMillis() - workload.getUpdated().getTime());
    }

    private static String runEnded(Workload workload) {
        return workload.getExitCode() != null
                ? "The VM exited with code " + workload.getExitCode()
                : "The VM is no longer running";
    }

    /**
     * Runs the microservice in a fresh VM on the project's node with its own machine identity, and
     * records the outcome: the VM and its module, and that the intent is answered, or why it could not
     * be, which waits for the next push or a restart.
     */
    private Future<Requeue> deploy(MicroserviceDeployment current, DeploymentState desired, ProjectDeployment target, String entryPoint) {
        return projectRepository.findById(current.getProjectId(), current.getOrganizationId())
                .compose(project -> {
                    Future<Requeue> ret;
                    if (project == null) {
                        ret = Future.succeededFuture(Requeue.NONE);
                    } else {
                        ret = deployWorkload(project, current, target, entryPoint)
                                .compose(workload -> microserviceDeploymentRepository.recordWorkload(current.getId(), workload.getId(), entryPoint)
                                                                                     .compose(v -> answered(current, desired,
                                                                                                            new DeploymentState(DeploymentStatusType.DEPLOYED, desired.commitSha()),
                                                                                                            null)))
                                .recover(error -> {
                                    log.error("Microservice {} of project {} could not be deployed", current.getName(), current.getProjectId(), error);
                                    return failIntent(current, desired, error.getMessage());
                                });
                    }
                    return ret;
                });
    }

    private Future<Workload> deployWorkload(Project project, MicroserviceDeployment deployment, ProjectDeployment target, String entryPoint) {
        return projectDeployIdentityService.issueRuntimeCredentials(project, deployment)
                .compose(credentials -> workloadOrchestrationService.deployWorkload(
                        projectWorkloadFactory.runtime(project, target.getNodeId(), target.getHostDir(), deployment, entryPoint, credentials)));
    }

    // The record answers the intent with what the microservice is, and why when it is not running as it should
    private Future<Requeue> answered(MicroserviceDeployment current, DeploymentState desired, DeploymentState observed, String failure) {
        return microserviceDeploymentRepository.recordFailure(current.getId(), failure)
                .compose(v -> microserviceDeploymentRepository.reportObserved(current.getId(), observed, current.getState().getGeneration(),
                                                                              "deploy of " + desired.commitSha()))
                .map(Requeue.NONE);
    }

    private Future<Requeue> failIntent(MicroserviceDeployment current, DeploymentState desired, String message) {
        return answered(current, desired, new DeploymentState(DeploymentStatusType.FAILED, liveCommit(current)), message);
    }

    // The commit the microservice serves as the record stood: a failed deployment leaves it live
    private static String liveCommit(MicroserviceDeployment deployment) {
        DeploymentState observed = deployment.getState().getObserved();
        return observed != null ? observed.commitSha() : null;
    }

    /**
     * Stops the microservice's VM when one runs, removes its machine identity, and deletes the record.
     * What is already gone is not a failure: a VM on a node the platform cannot reach is cut off by
     * the removal of its machine at its next reconnect.
     */
    private Future<Requeue> finalizeRemoval(MicroserviceDeployment current) {
        log.info("Removing microservice {} of project {}", current.getName(), current.getProjectId());
        return workloadOf(current)
                .compose(workload -> workload != null && workload.getStatus().isOpen()
                        ? workloadOrchestrationService.stopWorkload(workload.getId())
                                                      .recover(error -> {
                                                          log.warn("Workload {} of microservice {} could not be stopped: {}",
                                                                   workload.getId(), current.getName(), error.getMessage());
                                                          return Future.succeededFuture();
                                                      })
                        : Future.succeededFuture())
                .compose(v -> removeMachine(current))
                .compose(v -> microserviceDeploymentRepository.deleteByIdSync(current.getId()))
                .map(Requeue.NONE);
    }

    // An org member may already have removed the machine from the console
    private Future<Void> removeMachine(MicroserviceDeployment deployment) {
        Future<Void> ret;
        if (deployment.getMachineIdentityId() == null) {
            ret = Future.succeededFuture();
        } else {
            ret = participantIdentityService.deleteById(deployment.getMachineIdentityId())
                    .recover(error -> {
                        log.warn("Machine {} of microservice {} could not be removed: {}",
                                 deployment.getMachineIdentityId(), deployment.getName(), error.getMessage());
                        return Future.succeededFuture();
                    });
        }
        return ret;
    }
}
