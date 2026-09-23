package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.model.DeploymentStatus;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.management.api.model.MicroserviceDeployment;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.system.api.services.DeploymentOperationsService;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.config.UiDeploymentProperties;
import org.kinotic.system.api.services.SiteStorageService;
import org.kinotic.system.api.services.UiDeploymentProvisioner;
import org.kinotic.system.api.services.WorkloadOrchestrationService;
import org.kinotic.system.api.services.WorkloadService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDeploymentOperationsService implements DeploymentOperationsService {

    /** Longer than deleting a site takes, and short enough that a leaked URL is soon worthless. */
    private static final Duration REMOVAL_URL_TTL = Duration.ofMinutes(15);

    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;
    private final UiDeploymentRepository uiDeploymentRepository;
    private final WorkloadService workloadService;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final ParticipantIdentityService participantIdentityService;
    private final UiDeploymentProvisioner uiDeploymentProvisioner;
    private final SiteStorageService siteStorageService;
    private final SiteWorkloadFactory siteWorkloadFactory;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectDeployIdentityService projectDeployIdentityService;
    private final ProjectWorkloadFactory projectWorkloadFactory;
    private final KinoticSystemApiProperties properties;

    @Override
    public Future<Void> restartMicroservice(String deploymentId) {
        return loadMicroservice(deploymentId)
                .compose(deployment -> {
                    if (deployment.getEntryPoint() == null) {
                        throw new IllegalStateException("Microservice " + deployment.getName()
                                + " has never been deployed; deploy the project first");
                    }
                    return Future.all(projectRepository.findById(deployment.getProjectId(), deployment.getOrganizationId()),
                                      projectDeploymentRepository.findById(deployment.getProjectId(), deployment.getOrganizationId()))
                            .compose(found -> {
                                Project project = found.resultAt(0);
                                ProjectDeployment target = found.resultAt(1);
                                if (project == null || target == null || target.getNodeId() == null) {
                                    throw new IllegalStateException("Project " + deployment.getProjectId()
                                            + " has no deployment to run microservice " + deployment.getName() + " on; deploy the project first");
                                }
                                return stopRunningWorkload(deployment)
                                        .compose(v -> projectDeployIdentityService.issueRuntimeCredentials(project, deployment))
                                        .compose(credentials -> workloadOrchestrationService.deployWorkload(
                                                projectWorkloadFactory.runtime(project, target.getNodeId(), target.getHostDir(),
                                                                               deployment.getName(), deployment.getEntryPoint(), credentials)))
                                        .map(workload -> deployment.setWorkloadId(workload.getId())
                                                                   .setStatus(new DeploymentStatus(DeploymentStatusType.DEPLOYED)))
                                        .recover(error -> {
                                            log.error("Microservice {} of project {} could not be restarted", deployment.getName(), deployment.getProjectId(), error);
                                            return Future.succeededFuture(deployment.setStatus(new DeploymentStatus(DeploymentStatusType.FAILED, error.getMessage())));
                                        })
                                        .compose(updated -> microserviceDeploymentRepository.save(updated.setUpdated(new Date())))
                                        .compose(saved -> saved.getStatus().type() == DeploymentStatusType.FAILED
                                                ? Future.failedFuture(new IllegalStateException(saved.getStatus().message()))
                                                : Future.succeededFuture());
                            });
                });
    }

    // The node removes the VM of a run that ended, so only a running one needs stopping before its
    // replacement takes its place; its record stays as the run's outcome
    private Future<Void> stopRunningWorkload(MicroserviceDeployment deployment) {
        Future<Void> ret;
        if (deployment.getWorkloadId() == null) {
            ret = Future.succeededFuture();
        } else {
            ret = workloadService.findById(deployment.getWorkloadId())
                    .compose(workload -> workload == null || workload.getStatus().isComplete()
                            ? Future.succeededFuture()
                            : workloadOrchestrationService.stopWorkload(workload.getId()))
                    .recover(error -> {
                        log.warn("Workload {} of microservice {} could not be stopped before its restart: {}",
                                 deployment.getWorkloadId(), deployment.getName(), error.getMessage());
                        return Future.succeededFuture();
                    });
        }
        return ret;
    }

    @Override
    public Future<Void> removeMicroservice(String deploymentId) {
        return loadMicroservice(deploymentId)
                .compose(deployment -> destroyWorkload(deployment)
                        .compose(v -> removeMachine(deployment))
                        // sync so the console's immediate re-query no longer lists it
                        .compose(v -> microserviceDeploymentRepository.deleteByIdSync(deployment.getId())));
    }

    // The workload may already be gone: destroyed with its node, or never created
    private Future<Void> destroyWorkload(MicroserviceDeployment deployment) {
        Future<Void> ret;
        if (deployment.getWorkloadId() == null) {
            ret = Future.succeededFuture();
        } else {
            ret = workloadOrchestrationService.destroyWorkload(deployment.getWorkloadId())
                    .recover(error -> {
                        log.warn("Workload {} of microservice {} could not be destroyed: {}",
                                 deployment.getWorkloadId(), deployment.getName(), error.getMessage());
                        return Future.succeededFuture();
                    });
        }
        return ret;
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

    @Override
    public Future<UiDeployment> checkUiSite(String deploymentId) {
        return loadUi(deploymentId)
                .compose(uiDeploymentProvisioner::checkProvisioning)
                .compose(checked -> uiDeploymentRepository.save(checked.setUpdated(new Date())));
    }

    @Override
    public Future<UiDeployment> provisionUiSite(String deploymentId) {
        return loadUi(deploymentId)
                .compose(uiDeploymentProvisioner::provision)
                .compose(row -> uiDeploymentRepository.save(row.setUpdated(new Date())));
    }

    @Override
    public Future<Void> removeUiSite(String deploymentId) {
        return loadUi(deploymentId)
                .compose(deployment -> deleteFiles(deployment)
                        // sync so the console's immediate re-query no longer lists it
                        .compose(v -> uiDeploymentRepository.deleteByIdSync(deployment.getId())));
    }

    /**
     * Deletes the site's directory through a removal workload on the node its project deploys
     * to, with a URL scoped to that directory. A project never deployed has no node, and its
     * site no files; a removal that fails leaves the files for a later publish of the same
     * label to adopt; the removal workload's logs stay in the organization's log store.
     */
    private Future<Void> deleteFiles(UiDeployment deployment) {
        return projectDeploymentRepository.findById(deployment.getProjectId(), deployment.getOrganizationId())
                .compose(project -> {
                    Future<Void> ret;
                    if (project == null || project.getNodeId() == null) {
                        ret = Future.succeededFuture();
                    } else {
                        ret = siteStorageService.issueRemovalUrl(uiDeployment().resolveHostname(deployment.getId()), REMOVAL_URL_TTL)
                                .map(url -> siteWorkloadFactory.removal(deployment, project.getNodeId(), url))
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
                    log.warn("Files of site {} could not be deleted: {}", deployment.getId(), error.getMessage());
                    return Future.succeededFuture();
                });
    }

    private Future<MicroserviceDeployment> loadMicroservice(String deploymentId) {
        Validate.notBlank(deploymentId, "deploymentId is required");
        return microserviceDeploymentRepository.findById(deploymentId)
                .map(deployment -> {
                    if (deployment == null) {
                        throw new IllegalArgumentException("Microservice deployment not found: " + deploymentId);
                    }
                    return deployment;
                });
    }

    private Future<UiDeployment> loadUi(String deploymentId) {
        Validate.notBlank(deploymentId, "deploymentId is required");
        return uiDeploymentRepository.findById(deploymentId)
                .map(deployment -> {
                    if (deployment == null) {
                        throw new IllegalArgumentException("UI deployment not found: " + deploymentId);
                    }
                    return deployment;
                });
    }

    private UiDeploymentProperties uiDeployment() {
        return properties.getSystemApi().getUiDeployment();
    }

}
