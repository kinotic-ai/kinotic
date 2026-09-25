package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.model.MicroserviceDeployment;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.services.DeploymentOperationsProxy;
import org.kinotic.management.api.services.MicroserviceDeploymentService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultMicroserviceDeploymentService implements MicroserviceDeploymentService {

    private final SecurityContext securityContext;
    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;
    private final DeploymentOperationsProxy operations;

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
