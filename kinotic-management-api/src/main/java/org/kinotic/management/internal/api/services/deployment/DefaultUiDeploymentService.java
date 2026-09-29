package org.kinotic.management.internal.api.services.deployment;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.model.deployment.UiDeployment;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.management.api.services.deployment.DeploymentOperationsProxy;
import org.kinotic.management.api.services.deployment.UiDeploymentService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultUiDeploymentService implements UiDeploymentService {

    private final SecurityContext securityContext;
    private final UiDeploymentRepository uiDeploymentRepository;
    private final DeploymentOperationsProxy operations;

    @Override
    public Future<List<UiDeployment>> findAllForProject(String projectId) {
        Validate.notBlank(projectId, "projectId is required");
        OrganizationParticipant participant = requireOrgParticipant();
        // rows carry the organization, so a project of another organization lists nothing
        return uiDeploymentRepository.findAllForProject(projectId)
                .map(deployments -> deployments.stream()
                                               .filter(deployment -> participant.getOrganizationId().equals(deployment.getOrganizationId()))
                                               .toList());
    }

    @Override
    public Future<Page<WatchEvent>> findHistory(String deploymentId, Pageable pageable) {
        Validate.notNull(pageable, "pageable is required");
        OrganizationParticipant participant = requireOrgParticipant();
        return findByIdAndOrg(deploymentId, participant.getOrganizationId())
                .compose(deployment -> uiDeploymentRepository.findHistory(deployment, pageable));
    }

    @Override
    public Future<Void> remove(String deploymentId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return findByIdAndOrg(deploymentId, participant.getOrganizationId())
                .compose(deployment -> operations.removeUiSite(deployment.getId()));
    }

    /** Finds the deployment with the id in the organization; a missing deployment and another organization's both fail as not found. */
    private Future<UiDeployment> findByIdAndOrg(String deploymentId, String organizationId) {
        Validate.notBlank(deploymentId, "deploymentId is required");
        return uiDeploymentRepository.findById(deploymentId)
                .map(deployment -> DomainUtil.requireOwned(deployment, organizationId, "UI deployment not found."));
    }

    private OrganizationParticipant requireOrgParticipant() {
        // ApplicationParticipant is a sibling type, so app end-users are rejected here.
        return securityContext.requireParticipant(OrganizationParticipant.class);
    }

}
