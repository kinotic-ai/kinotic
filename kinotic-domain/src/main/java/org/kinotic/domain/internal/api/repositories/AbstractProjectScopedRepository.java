package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.ProjectScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * The ids of the projects containing the documents with the given ids in {@code orgId}.
     */
    public Future<Set<String>> findProjectIdsOf(Collection<String> ids, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        Future<Set<String>> ret;
        if (ids.isEmpty()) {
            ret = Future.succeededFuture(Set.of());
        } else {
            ret = findAll(Pageable.ofSize(ids.size()), b -> b.routing(orgId).query(composeOrgFilter(orgId, idsFilter(ids))))
                    .map(page -> page.getContent().stream().map(ProjectScoped::getProjectId).collect(Collectors.toSet()));
        }
        return ret;
    }

    protected Query projectIdFilter(String projectId) {
        return termFilter("projectId", projectId);
    }
}
