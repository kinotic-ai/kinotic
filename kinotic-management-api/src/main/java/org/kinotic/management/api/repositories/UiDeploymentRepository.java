package org.kinotic.management.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Stores {@link UiDeployment}s, the standing deployments of a project's UI artifacts, keyed
 * by the site's hostname label and listed by project.
 */
@Component
public class UiDeploymentRepository extends AbstractRepository<UiDeployment> {

    /** More UIs than one project publishes, so a project's deployments are read in one page. */
    private static final int PROJECT_PAGE_SIZE = 500;

    public UiDeploymentRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_ui_deployment", UiDeployment.class, crudServiceTemplate);
    }

    /**
     * Lists the deployments of the project's UIs, ordered by name.
     *
     * @param projectId the project whose UI deployments to list
     * @return a future emitting the deployments, empty when the project has none
     */
    public Future<List<UiDeployment>> findAllForProject(String projectId) {
        Validate.notBlank(projectId, "projectId cannot be blank");
        return findAll(Pageable.ofSize(PROJECT_PAGE_SIZE), b -> b.query(termFilter("projectId", projectId)))
                .map(page -> page.getContent().stream()
                                 .sorted(Comparator.comparing(UiDeployment::getName))
                                 .toList());
    }
}
