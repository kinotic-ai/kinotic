package org.kinotic.core.api.crud;

import java.util.concurrent.CompletableFuture;

/**
 * Extends {@link IdentifiableCrudService} with queries scoped to an application.
 * Implementations automatically enforce organization-level filtering when the
 * caller is authenticated under an organization scope.
 *
 * @param <T>  the entity type
 * @param <ID> the id type
 */
public interface ApplicationScopedCrudService<T extends Identifiable<ID>, ID> extends IdentifiableCrudService<T, ID> {

    /**
     * Returns the number of entities that belong to the given application.
     *
     * @param applicationId the application to count entities for
     * @return a {@link CompletableFuture} emitting the count
     */
    CompletableFuture<Long> countForApplication(String applicationId);

    /**
     * Returns a {@link Page} of entities that belong to the given application.
     *
     * @param applicationId the application to find entities for
     * @param pageable      the paging parameters
     * @return a {@link CompletableFuture} emitting a page of entities
     */
    CompletableFuture<Page<T>> findAllForApplication(String applicationId, Pageable pageable);

}
