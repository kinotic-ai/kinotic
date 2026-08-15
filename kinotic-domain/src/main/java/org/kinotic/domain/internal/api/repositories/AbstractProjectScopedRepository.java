package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.ProjectScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

/**
 * Repository tier for entities that belong to a project within an application and organization.
 * Adds {@code projectId}-scoped query helpers, each with an {@code orgId}-aware overload.
 */
public abstract class AbstractProjectScopedRepository<T extends ProjectScoped<String>>
        extends AbstractApplicationScopedRepository<T> {

    public AbstractProjectScopedRepository(String indexName,
                                           Class<T> type,
                                           CrudServiceTemplate crudServiceTemplate) {
        super(indexName, type, crudServiceTemplate);
    }

    public Future<Long> countForProject(String projectId, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return count(b -> b.routing(orgId).query(composeOrgFilter(orgId, projectIdFilter(projectId))));
    }

    public Future<Page<T>> findAllForProject(String projectId, String orgId, Pageable pageable) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findAll(pageable, b -> b.routing(orgId).query(composeOrgFilter(orgId, projectIdFilter(projectId))));
    }

    protected Query projectIdFilter(String projectId) {
        return termFilter("projectId", projectId);
    }
}
