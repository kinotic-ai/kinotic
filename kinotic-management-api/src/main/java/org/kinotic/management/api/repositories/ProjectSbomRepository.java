package org.kinotic.management.api.repositories;

import org.kinotic.domain.internal.api.repositories.AbstractApplicationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.deployment.ProjectSbom;
import org.springframework.stereotype.Component;

@Component
public class ProjectSbomRepository extends AbstractApplicationScopedRepository<ProjectSbom> {

    public ProjectSbomRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_project_sbom", ProjectSbom.class, crudServiceTemplate);
    }

}
