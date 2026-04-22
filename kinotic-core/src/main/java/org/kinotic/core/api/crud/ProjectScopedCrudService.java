package org.kinotic.core.api.crud;

import java.util.concurrent.CompletableFuture;

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
     * @return a {@link CompletableFuture} emitting the count
     */
    CompletableFuture<Long> countForProject(String projectId);

    /**
     * Returns a {@link Page} of entities that belong to the given project.
     *
     * @param projectId the project to find entities for
     * @param pageable  the paging parameters
     * @return a {@link CompletableFuture} emitting a page of entities
     */
    CompletableFuture<Page<T>> findAllForProject(String projectId, Pageable pageable);

}
