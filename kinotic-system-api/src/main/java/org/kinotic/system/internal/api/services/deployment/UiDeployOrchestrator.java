package org.kinotic.system.internal.api.services.deployment;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.services.Reconciler;
import org.kinotic.domain.api.model.Requeue;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.management.api.model.deployment.DeploymentState;
import org.kinotic.management.api.model.deployment.DeploymentStatus;
import org.kinotic.management.api.model.deployment.DeploymentStatusType;
import org.kinotic.management.api.model.deployment.UiDeployment;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.services.deployment.SiteStorageService;
import org.kinotic.system.api.services.deployment.UiDeploymentProvisioner;
import org.kinotic.system.api.services.workload.WorkloadOrchestrationService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Keeps each UI of a deployed project served as its deployment says it should: the worker the
 * reconcile master calls for a {@link UiDeployment} when its intent changes and when it is found not
 * in its desired state. The files are uploaded by the deploy job; this worker checks that the site
 * serves the published commit, records what it observed while it does not, and asks to look again
 * shortly. A deployment for which the commit dropped the UI is left as it is, its site serving, and
 * one whose removal was asked for is finalized: its files deleted through a removal workload on the
 * project's node, then its record.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UiDeployOrchestrator implements Reconciler<UiDeployment> {

    /** How long between checks of a site that does not yet serve its commit. */
    static final Duration CHECK_INTERVAL = Duration.ofSeconds(30);

    /** Longer than deleting a site takes, and short enough that a leaked URL is soon worthless. */
    private static final Duration REMOVAL_URL_TTL = Duration.ofMinutes(15);

    private final UiDeploymentRepository uiDeploymentRepository;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final UiDeploymentProvisioner uiDeploymentProvisioner;
    private final SiteStorageService siteStorageService;
    private final SiteWorkloadFactory siteWorkloadFactory;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final KinoticSystemApiProperties properties;

    @Override
    public WatchedType type() {
        return WatchedType.UI_DEPLOYMENT;
    }

    @Override
    public Future<Requeue> reconcile(UiDeployment current) {
        ReconcileState<DeploymentState> state = current.getState();
        DeploymentState desired = state.getDesired();
        Future<Requeue> ret;
        if (state.getDeletionRequested() != null) {
            ret = finalizeRemoval(current);
        } else if (desired == null) {
            ret = Future.succeededFuture(Requeue.NONE);
        } else if (desired.phase() == DeploymentStatusType.ORPHANED) {
            // the commit dropped the UI: the site keeps serving until the deployment is removed
            ret = uiDeploymentRepository.reportObserved(current.getId(), desired, state.getGeneration(), "orphaned by the commit")
                                        .map(Requeue.NONE);
        } else if (state.getObservedGeneration() >= state.getGeneration() && desired.equals(state.getObserved())) {
            // the site was seen serving this publish; the next publish, not the master's initial list or
            // resync, is what asks the site again
            ret = Future.succeededFuture(Requeue.NONE);
        } else {
            ret = uiDeploymentProvisioner.check(current, desired.commitSha())
                                         .compose(status -> record(current, desired, status));
        }
        return ret;
    }

    // Serving the commit answers the intent; anything else is what the site does meanwhile, and a look again
    private Future<Requeue> record(UiDeployment current, DeploymentState desired, DeploymentStatus status) {
        ReconcileState<DeploymentState> state = current.getState();
        Future<Requeue> ret;
        if (status.type() == DeploymentStatusType.READY) {
            log.info("Site {} serves UI {} of project {} at commit {}", current.getId(), current.getName(), current.getProjectId(), desired.commitSha());
            ret = uiDeploymentRepository.recordObservation(current.getId(), null)
                    .compose(v -> uiDeploymentRepository.reportObserved(current.getId(), new DeploymentState(DeploymentStatusType.READY, desired.commitSha()),
                                                                        state.getGeneration(), "site check"))
                    .map(Requeue.NONE);
        } else {
            DeploymentState observed = new DeploymentState(DeploymentStatusType.PROVISIONING, DeploymentState.commitOf(current.getState().getObserved()));
            // the observation changes with every check and is worth a write only when it says something new
            Future<Void> noted = Objects.equals(status.message(), current.getObservation())
                    ? Future.succeededFuture()
                    : uiDeploymentRepository.recordObservation(current.getId(), status.message());
            ret = noted.compose(v -> uiDeploymentRepository.reportObserved(current.getId(), observed, state.getGeneration(), "site check"))
                       .map(Requeue.after(CHECK_INTERVAL));
        }
        return ret;
    }

    /**
     * Deletes the site's directory through a removal workload on the node its project deploys to,
     * with a URL scoped to that directory, then the record. A project never deployed has no node,
     * and its site no files; a removal that fails leaves the files for a later publish of the same
     * label to adopt; the removal workload's logs stay in the organization's log store.
     */
    private Future<Requeue> finalizeRemoval(UiDeployment current) {
        log.info("Removing site {} of UI {} of project {}", current.getId(), current.getName(), current.getProjectId());
        return projectDeploymentRepository.findById(current.getProjectId(), current.getOrganizationId())
                .compose(project -> {
                    Future<Void> ret;
                    if (project == null || project.getNodeId() == null) {
                        ret = Future.succeededFuture();
                    } else {
                        ret = siteStorageService.issueRemovalUrl(properties.getSystemApi().getUiDeployment().resolveHostname(current.getId()), REMOVAL_URL_TTL)
                                .map(url -> siteWorkloadFactory.removal(current, project.getNodeId(), url))
                                .compose(workloadOrchestrationService::deployWorkload)
                                .compose(finished -> {
                                    Future<Void> removed;
                                    if (finished.getStatus() == WorkloadStatus.STOPPED && Integer.valueOf(0).equals(finished.getExitCode())) {
                                        removed = Future.succeededFuture();
                                    } else {
                                        removed = Future.failedFuture(new IllegalStateException("Removal workload " + finished.getId()
                                                + " ended " + finished.getStatus() + " with exit code " + finished.getExitCode()));
                                    }
                                    return removed;
                                });
                    }
                    return ret;
                })
                .recover(error -> {
                    log.warn("Files of site {} could not be deleted: {}", current.getId(), error.getMessage());
                    return Future.succeededFuture();
                })
                .compose(v -> uiDeploymentRepository.deleteByIdSync(current.getId()))
                .map(Requeue.NONE);
    }
}
