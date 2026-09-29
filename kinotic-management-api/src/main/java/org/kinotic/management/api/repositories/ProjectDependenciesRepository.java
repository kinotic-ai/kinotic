package org.kinotic.management.api.repositories;

import org.kinotic.domain.internal.api.repositories.AbstractApplicationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.deployment.ProjectDependencies;
import org.springframework.stereotype.Component;

/**
 * Stores each project's {@link ProjectDependencies}, one document per project keyed by the project
 * id.
 */
@Component
public class ProjectDependenciesRepository extends AbstractApplicationScopedRepository<ProjectDependencies> {

    public ProjectDependenciesRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_project_dependencies", ProjectDependencies.class, crudServiceTemplate);
    }

}
