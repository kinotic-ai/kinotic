package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.OrganizationParticipant;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.services.ProjectDeploymentService;
import org.kinotic.management.internal.api.repositories.ProjectDeploymentRepository;
import org.springframework.stereotype.Component;

/**
 * Default impl reading the {@code kinotic_project_deployment} index, scoped to the
 * caller's organization from the {@link SecurityContext}.
 */
@Component
@RequiredArgsConstructor
public class DefaultProjectDeploymentService implements ProjectDeploymentService {

    private final ProjectDeploymentRepository deploymentRepository;
    private final SecurityContext securityContext;

    @Override
    public Future<ProjectDeployment> findByProjectId(String projectId) {
        String organizationId = securityContext.requireParticipant(OrganizationParticipant.class)
                                               .getOrganizationId();
        return deploymentRepository.findById(projectId, organizationId);
    }

}
