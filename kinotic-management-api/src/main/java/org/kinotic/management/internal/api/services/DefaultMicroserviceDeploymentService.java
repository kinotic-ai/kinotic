package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.DeploymentStatus;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.model.MicroserviceDeployment;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.WorkloadRepository;
import org.kinotic.management.api.services.DeploymentOperationsProxy;
import org.kinotic.management.api.services.MicroserviceDeploymentService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultMicroserviceDeploymentService implements MicroserviceDeploymentService {

    private final SecurityContext securityContext;
    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;
    private final WorkloadRepository workloadRepository;
    private final DeploymentOperationsProxy operations;

    @Override
    public Future<List<MicroserviceDeployment>> findAllForProject(String projectId) {
        Validate.notBlank(projectId, "projectId is required");
        OrganizationParticipant participant = requireOrgParticipant();
        // rows carry the organization, so a project of another organization lists nothing
        return microserviceDeploymentRepository.findAllForProject(projectId)
                .map(deployments -> deployments.stream()
                                               .filter(deployment -> participant.getOrganizationId().equals(deployment.getOrganizationId()))
                                               .toList())
                .compose(deployments -> Future.all(deployments.stream().map(this::withRunState).toList())
                                              .map(all -> all.<MicroserviceDeployment>list()));
    }

    /**
     * The deployment as its VM stands: a row records the outcome of deploying, and the workload
     * the outcome of running, so a row still reading DEPLOYED whose run has ended is reported
     * FAILED with the run's exit.
     */
    private Future<MicroserviceDeployment> withRunState(MicroserviceDeployment deployment) {
        Future<MicroserviceDeployment> ret;
        if (deployment.getWorkloadId() == null || deployment.getStatus().type() != DeploymentStatusType.DEPLOYED) {
            ret = Future.succeededFuture(deployment);
        } else {
            ret = workloadRepository.findById(deployment.getWorkloadId())
                    .map(workload -> workload != null && workload.getStatus().isComplete()
                            ? deployment.setStatus(new DeploymentStatus(DeploymentStatusType.FAILED, runEnded(workload)))
                            : deployment);
        }
        return ret;
    }

    private static String runEnded(Workload workload) {
        return workload.getExitCode() != null
                ? "The VM exited with code " + workload.getExitCode()
                : "The VM is no longer running";
    }

    @Override
    public Future<MicroserviceDeployment> restart(String deploymentId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwned(deploymentId, participant)
                .compose(deployment -> operations.restartMicroservice(deployment.getId()).map(deployment));
    }

    @Override
    public Future<Void> remove(String deploymentId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwned(deploymentId, participant)
                .compose(deployment -> operations.removeMicroservice(deployment.getId()));
    }

    /** Loads a deployment of the participant's organization; another organization's is indistinguishable from none. */
    private Future<MicroserviceDeployment> loadOwned(String deploymentId, OrganizationParticipant participant) {
        Validate.notBlank(deploymentId, "deploymentId is required");
        return microserviceDeploymentRepository.findById(deploymentId)
                .map(deployment -> DomainUtil.requireOwned(deployment, participant.getOrganizationId(), "Microservice deployment not found."));
    }

    private OrganizationParticipant requireOrgParticipant() {
        // ApplicationParticipant is a sibling type, so app end-users are rejected here.
        return securityContext.requireParticipant(OrganizationParticipant.class);
    }

}
