package org.kinotic.management.internal.api.services.deployment;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.management.api.model.deployment.ProjectSbom;
import org.kinotic.management.api.repositories.ProjectSbomRepository;
import org.kinotic.management.api.services.deployment.DeploymentOperationsProxy;
import org.kinotic.management.api.services.deployment.ProjectSbomService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultProjectSbomService implements ProjectSbomService {

    private final SecurityContext securityContext;
    private final ProjectSbomRepository projectSbomRepository;
    private final DeploymentOperationsProxy operations;

    @Override
    public Future<ProjectSbom> findSbom(String projectId) {
        Validate.notBlank(projectId, "projectId is required");
        return projectSbomRepository.findById(projectId, requireOrganizationId());
    }

    @Override
    public Future<String> findDocumentUrl(String projectId) {
        Validate.notBlank(projectId, "projectId is required");
        String organizationId = requireOrganizationId();
        // rows are stored per organization, so another organization's project reads as one without an SBOM
        return projectSbomRepository.findById(projectId, organizationId)
                .compose(sbom -> sbom != null
                        ? operations.issueSbomUrl(organizationId, projectId, sbom.getCommitSha())
                        : Future.succeededFuture());
    }

    private String requireOrganizationId() {
        // ApplicationParticipant is a sibling type, so app end-users are rejected here.
        return securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
    }

}
