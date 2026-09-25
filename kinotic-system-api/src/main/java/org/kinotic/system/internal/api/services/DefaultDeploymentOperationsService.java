package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.management.api.model.MicroserviceDeployment;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.system.api.services.DeploymentOperationsService;
import org.kinotic.system.api.services.WorkloadOrchestrationService;
import org.kinotic.system.api.services.WorkloadService;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDeploymentOperationsService implements DeploymentOperationsService {

    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;
    private final UiDeploymentRepository uiDeploymentRepository;
    private final WorkloadService workloadService;
    private final WorkloadOrchestrationService workloadOrchestrationService;

    @Override
    public Future<Void> restartMicroservice(String deploymentId) {
        return loadMicroservice(deploymentId)
                .compose(deployment -> {
                    Future<Void> ret;
                    if (deployment.getState().getDesired() == null) {
                        ret = Future.failedFuture(new IllegalStateException("Microservice " + deployment.getName()
                                + " has never been deployed; deploy the project first"));
                    } else if (deployment.getWorkloadId() == null) {
                        ret = microserviceDeploymentRepository.renewDesired(deployment.getId(), "restartMicroservice");
                    } else {
                        // a VM still running is stopped, and the run's end brings its worker back to replace it; one
                        // that is not asks the worker to answer the intent again
                        ret = workloadService.findById(deployment.getWorkloadId())
                                .compose(workload -> workload != null && workload.getStatus().isOpen()
                                        ? workloadOrchestrationService.stopWorkload(workload.getId())
                                        : microserviceDeploymentRepository.renewDesired(deployment.getId(), "restartMicroservice"));
                    }
                    return ret;
                });
    }

    @Override
    public Future<Void> removeMicroservice(String deploymentId) {
        return loadMicroservice(deploymentId)
                .compose(deployment -> microserviceDeploymentRepository.requestDeletion(deployment.getId(), "removeMicroservice"));
    }

    @Override
    public Future<Void> removeUiSite(String deploymentId) {
        return loadUi(deploymentId)
                .compose(deployment -> uiDeploymentRepository.requestDeletion(deployment.getId(), "removeUiSite"));
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

}
