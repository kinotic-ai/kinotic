package org.kinotic.management.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.management.api.model.MicroserviceDeployment;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Stores {@link MicroserviceDeployment}s, the standing deployments of a project's microservice
 * artifacts, looked up by project and by artifact name.
 */
@Component
public class MicroserviceDeploymentRepository extends AbstractRepository<MicroserviceDeployment> {

    /** More microservices than one project deploys, so a project's deployments are read in one page. */
    private static final int PROJECT_PAGE_SIZE = 500;

    public MicroserviceDeploymentRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_microservice_deployment", MicroserviceDeployment.class, crudServiceTemplate);
    }

    /**
     * Finds the deployment of the named microservice of the project.
     *
     * @param projectId the project the microservice belongs to
     * @param name      the microservice's artifact name
     * @return a future emitting the deployment, or {@code null} when the project has none by
     *         that name
     */
    public Future<MicroserviceDeployment> findByProjectAndName(String projectId, String name) {
        Validate.notBlank(projectId, "projectId cannot be blank");
        Validate.notBlank(name, "name cannot be blank");
        return findFirst(b -> b.query(composeFilter(termFilter("projectId", projectId),
                                                    termFilter("name", name))));
    }

    /**
     * Lists the deployments of the project's microservices, ordered by name.
     *
     * @param projectId the project whose microservice deployments to list
     * @return a future emitting the deployments, empty when the project has none
     */
    public Future<List<MicroserviceDeployment>> findAllForProject(String projectId) {
        Validate.notBlank(projectId, "projectId cannot be blank");
        return findAll(Pageable.ofSize(PROJECT_PAGE_SIZE), b -> b.query(termFilter("projectId", projectId)))
                .map(page -> page.getContent().stream()
                                 .sorted(Comparator.comparing(MicroserviceDeployment::getName))
                                 .toList());
    }
}
