package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;

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
     * @return a {@link Future} emitting the count
     */
    Future<Long> countForApplication(String applicationId);

    /**
     * Returns a {@link Page} of entities that belong to the given application.
     *
     * @param applicationId the application to find entities for
     * @param pageable      the paging parameters
     * @return a {@link Future} emitting a page of entities
     */
    Future<Page<T>> findAllForApplication(String applicationId, Pageable pageable);

}
