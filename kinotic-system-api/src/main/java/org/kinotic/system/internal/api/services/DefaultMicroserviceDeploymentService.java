package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.management.api.model.MicroserviceDeployment;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.system.api.services.MicroserviceDeploymentService;
import org.kinotic.system.api.services.WorkloadOrchestrationService;
import org.kinotic.system.api.services.WorkloadService;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMicroserviceDeploymentService implements MicroserviceDeploymentService {

    private final SecurityContext securityContext;
    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;
    private final WorkloadService workloadService;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final ParticipantIdentityService participantIdentityService;

    @Override
    public Future<List<MicroserviceDeployment>> findAllForProject(String projectId) {
        Validate.notBlank(projectId, "projectId is required");
        OrganizationParticipant participant = requireOrgParticipant();
        // rows carry the organization, so a project of another organization lists nothing
        return microserviceDeploymentRepository.findAllForProject(projectId)
                .map(deployments -> deployments.stream()
                                               .filter(deployment -> participant.getOrganizationId().equals(deployment.getOrganizationId()))
                                               .toList());
    }

    @Override
    public Future<MicroserviceDeployment> restart(String deploymentId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwned(deploymentId, participant)
                .compose(deployment -> {
                    if (deployment.getWorkloadId() == null) {
                        throw new IllegalStateException("Microservice " + deployment.getName()
                                + " has no workload to restart; deploy the project again");
                    }
                    return workloadService.findById(deployment.getWorkloadId())
                            .compose(workload -> {
                                if (workload == null) {
                                    throw new IllegalStateException("The workload of microservice " + deployment.getName()
                                            + " no longer exists; deploy the project again");
                                }
                                // restartWorkload only boots a stopped VM, so a running one is stopped first
                                Future<Void> stopped = workload.getStatus().isComplete()
                                        ? Future.succeededFuture()
                                        : workloadOrchestrationService.stopWorkload(workload.getId());
                                return stopped.compose(v -> workloadOrchestrationService.restartWorkload(workload.getId()));
                            })
                            .map(restarted -> deployment);
                });
    }

    @Override
    public Future<Void> remove(String deploymentId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwned(deploymentId, participant)
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

    /** Loads a deployment of the participant's organization; another organization's is indistinguishable from none. */
    private Future<MicroserviceDeployment> loadOwned(String deploymentId, OrganizationParticipant participant) {
        Validate.notBlank(deploymentId, "deploymentId is required");
        return microserviceDeploymentRepository.findById(deploymentId)
                .map(deployment -> {
                    if (deployment == null || !participant.getOrganizationId().equals(deployment.getOrganizationId())) {
                        throw new IllegalArgumentException("Microservice deployment not found.");
                    }
                    return deployment;
                });
    }

    private OrganizationParticipant requireOrgParticipant() {
        // ApplicationParticipant is a sibling type, so app end-users are rejected here.
        return securityContext.requireParticipant(OrganizationParticipant.class);
    }

}
