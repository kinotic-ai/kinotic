package org.kinotic.persistence.internal.api.services;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.ProjectScopedCrudService;
import org.kinotic.persistence.api.model.EntityDefinition;

import java.util.concurrent.CompletableFuture;

/**
 * Internal DAO for {@link EntityDefinition} persistence. Extends {@link ProjectScopedCrudService}
 * so standard application- and project-scoped queries are inherited. Exists as a separate interface
 * from {@link org.kinotic.persistence.api.services.EntityDefinitionService} to break circular
 * Spring bean references between the definition service and the entity service cache.
 */
public interface EntityDefinitionDAO extends ProjectScopedCrudService<EntityDefinition, String> {

    /**
     * Finds all published {@link EntityDefinition}s for the given application.
     *
     * @param applicationId the application to find definitions for
     * @param pageable      the paging parameters
     * @return a {@link CompletableFuture} emitting a page of published entity definitions
     */
    CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId, Pageable pageable);

}
