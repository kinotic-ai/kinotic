package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;

/**
 * Extends {@link ApplicationScopedCrudService} with queries scoped to a project.
 * Implementations automatically enforce organization-level filtering when the
 * caller is authenticated under an organization scope.
 *
 * @param <T>  the entity type
 * @param <ID> the id type
 */
public interface ProjectScopedCrudService<T extends Identifiable<ID>, ID> extends ApplicationScopedCrudService<T, ID> {

    /**
     * Returns the number of entities that belong to the given project.
     *
     * @param projectId the project to count entities for
     * @return a {@link Future} emitting the count
     */
    Future<Long> countForProject(String projectId);

    /**
     * Returns a {@link Page} of entities that belong to the given project.
     *
     * @param projectId the project to find entities for
     * @param pageable  the paging parameters
     * @return a {@link Future} emitting a page of entities
     */
    Future<Page<T>> findAllForProject(String projectId, Pageable pageable);

}
