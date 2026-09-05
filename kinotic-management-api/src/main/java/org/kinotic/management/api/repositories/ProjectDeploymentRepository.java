package org.kinotic.management.api.repositories;

import org.kinotic.domain.internal.api.repositories.AbstractApplicationScopedRepository;

import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProjectDeploymentRepository extends AbstractApplicationScopedRepository<ProjectDeployment> {

    public ProjectDeploymentRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_project_deployment", ProjectDeployment.class, crudServiceTemplate);
    }
}
