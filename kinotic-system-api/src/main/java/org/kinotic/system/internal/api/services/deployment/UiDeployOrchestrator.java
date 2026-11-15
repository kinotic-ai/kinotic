package org.kinotic.system.internal.api.services.deployment;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.Reconciler;
import org.kinotic.domain.api.model.Requeue;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.management.api.model.deployment.DeploymentState;
import org.kinotic.management.api.model.deployment.DeploymentStatus;
import org.kinotic.management.api.model.deployment.DeploymentStatusType;
import org.kinotic.management.api.model.deployment.UiDeployment;
import org.kinotic.management.api.model.workload.Workload;
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
import java.util.function.Function;

/**
 * Keeps each UI of a deployed project served as its deployment says it should: the worker the
 * reconcile master calls for a {@link UiDeployment} when its intent changes and when it is found not
 * in its desired state. The files are uploaded by the deploy job; this worker checks that the site
 * serves the published commit, records what it observed while it does not, and asks to look again
 * shortly. A deployment for which the commit dropped the UI is left as it is, its site serving, and
 * one whose removal was asked for is finalized: its files deleted through a removal workload, then its
 * record. While the site's organization is deactivated, a suspension workload has the site redirect
 * every route to the portal's page saying so, and once it is active again a resumption workload has it
 * serve its own index.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UiDeployOrchestrator implements Reconciler<UiDeployment> {

    /** How long between checks of a site that does not yet serve its commit. */
    static final Duration CHECK_INTERVAL = Duration.ofSeconds(30);

    /** Longer than deleting a site, or switching its index, takes, and short enough that a leaked URL is soon worthless. */
    private static final Duration SITE_URL_TTL = Duration.ofMinutes(15);

    /** The portal's page saying an organization is deactivated, followed by the organization's id. */
    private static final String DEACTIVATED_PATH = "/deactivated/";

    private final UiDeploymentRepository uiDeploymentRepository;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final UiDeploymentProvisioner uiDeploymentProvisioner;
    private final SiteStorageService siteStorageService;
    private final SiteWorkloadFactory siteWorkloadFactory;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final OrganizationService organizationService;
    private final KinoticSystemApiProperties properties;
    private final KinoticDomainProperties domainProperties;

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
        } else {
            ret = organizationService.findById(current.getOrganizationId())
                                     .compose(organization -> organization != null && organization.getDeactivated() != null
                                             ? suspend(current)
                                             : resumeIfSuspended(current).compose(v -> reconcileIntent(current, desired)));
        }
        return ret;
    }

    private Future<Requeue> reconcileIntent(UiDeployment current, DeploymentState desired) {
        ReconcileState<DeploymentState> state = current.getState();
        Future<Requeue> ret;
        if (desired.phase() == DeploymentStatusType.ORPHANED) {
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

    // a publish while the organization is deactivated serves the site's own index again, and is a new generation,
    // so the site is suspended once more
    private Future<Requeue> suspend(UiDeployment current) {
        ReconcileState<DeploymentState> state = current.getState();
        Future<Requeue> ret;
        if (suspended(current) && state.getObservedGeneration() >= state.getGeneration()) {
            ret = Future.succeededFuture(Requeue.NONE);
        } else {
            String deactivatedUrl = domainProperties.getDomain().getPortalBaseUrl() + DEACTIVATED_PATH + current.getOrganizationId();
            ret = onSiteFiles(current, url -> siteWorkloadFactory.suspension(current, url, deactivatedUrl))
                    .compose(v -> uiDeploymentRepository.reportObserved(current.getId(),
                                                                        new DeploymentState(DeploymentStatusType.SUSPENDED,
                                                                                            DeploymentState.commitOf(state.getObserved())),
                                                                        state.getGeneration(),
                                                                        "organization deactivated"))
                    .map(Requeue.NONE);
        }
        return ret;
    }

    // the site's state is observed afresh once it serves its own index again
    private Future<Void> resumeIfSuspended(UiDeployment current) {
        return suspended(current)
                ? onSiteFiles(current, url -> siteWorkloadFactory.resumption(current, url))
                : Future.succeededFuture();
    }

    private static boolean suspended(UiDeployment deployment) {
        DeploymentState observed = deployment.getState().getObserved();
        return observed != null && observed.phase() == DeploymentStatusType.SUSPENDED;
    }

    // runs the workload built from an index URL for the site, where site storage is enabled
    private Future<Void> onSiteFiles(UiDeployment current, Function<String, Workload> workload) {
        Future<Void> ret;
        if (properties.getSystemApi().getUiDeployment().storesSites()) {
            ret = siteStorageService.issueIndexUrl(hostname(current), SITE_URL_TTL)
                                    .map(workload)
                                    .compose(this::runToCompletion);
        } else {
            ret = Future.succeededFuture();
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
     * Deletes the site's directory through a removal workload on any node with room, with a URL scoped
     * to that directory, then the record. A project never deployed has no node, and its site no files,
     * nor does a site in an environment whose site storage is disabled; a removal that fails keeps the
     * record, so the reconcile is retried until the files are gone; the removal workload's logs stay in
     * the organization's log store.
     */
    private Future<Requeue> finalizeRemoval(UiDeployment current) {
        log.info("Removing site {} of UI {} of project {}", current.getId(), current.getName(), current.getProjectId());
        return projectDeploymentRepository.findById(current.getProjectId(), current.getOrganizationId())
                .compose(project -> {
                    Future<Void> ret;
                    if (project == null || project.getNodeId() == null || !properties.getSystemApi().getUiDeployment().storesSites()) {
                        ret = Future.succeededFuture();
                    } else {
                        ret = siteStorageService.issueRemovalUrl(hostname(current), SITE_URL_TTL)
                                .map(url -> siteWorkloadFactory.removal(current, url))
                                .compose(this::runToCompletion);
                    }
                    return ret;
                })
                .compose(v -> uiDeploymentRepository.deleteByIdSync(current.getId()))
                .map(Requeue.NONE);
    }

    // a workload that does not stop with exit code 0 fails the reconcile, which is retried
    private Future<Void> runToCompletion(Workload workload) {
        return workloadOrchestrationService.deployWorkload(workload).compose(finished -> {
            Future<Void> ret;
            if (finished.getStatus() == WorkloadStatus.STOPPED && Integer.valueOf(0).equals(finished.getExitCode())) {
                ret = Future.succeededFuture();
            } else {
                ret = Future.failedFuture(new IllegalStateException(finished.getDescription() + " (workload " + finished.getId()
                        + ") ended " + finished.getStatus() + " with exit code " + finished.getExitCode()));
            }
            return ret;
        });
    }

    private String hostname(UiDeployment deployment) {
        return domainProperties.getDomain().getPublishedSites().resolveHostname(deployment.getId());
    }
}
