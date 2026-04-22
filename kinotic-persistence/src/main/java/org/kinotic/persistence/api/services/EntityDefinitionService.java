
package org.kinotic.persistence.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.ProjectScopedCrudService;
import org.kinotic.persistence.api.model.EntityDefinition;

import java.util.concurrent.CompletableFuture;

/**
 * Public service for managing {@link EntityDefinition} lifecycle: creation, publication,
 * un-publication, and deletion. Extends {@link ProjectScopedCrudService} so application-
 * and project-scoped queries are inherited with automatic organization enforcement.
 */
@Publish
public interface EntityDefinitionService extends ProjectScopedCrudService<EntityDefinition, String> {

    /**
     * Creates a new {@link EntityDefinition}. Validates the definition, derives the logical
     * index name, and persists it. The definition is not usable for data operations until
     * {@link #publish(String)} is called.
     *
     * @param entityDefinition the definition to create
     * @return a {@link CompletableFuture} emitting the saved definition with system-managed
     *         fields populated (id, created, itemIndex, etc.)
     */
    CompletableFuture<EntityDefinition> create(EntityDefinition entityDefinition);

    /**
     * Finds all published {@link EntityDefinition}s for the given application.
     *
     * @param applicationId the application to find definitions for
     * @param pageable      the paging parameters
     * @return a {@link CompletableFuture} emitting a page of published definitions
     */
    CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId, Pageable pageable);

    /**
     * Publishes the {@link EntityDefinition} with the given id, making it available for
     * data read and write operations.
     *
     * @param entityDefinitionId the id of the definition to publish
     * @return a {@link CompletableFuture} that completes when the definition has been published
     */
    CompletableFuture<Void> publish(String entityDefinitionId);

    /**
     * Forces an immediate Elasticsearch index refresh so recent writes to the entity
     * definition index are searchable. Reserved for test and batch-load scenarios.
     *
     * @return a {@link CompletableFuture} that completes when the refresh is done
     */
    CompletableFuture<Void> syncIndex();

    /**
     * Un-publishes the {@link EntityDefinition} with the given id, removing the
     * underlying data index and making the definition unavailable for data operations.
     *
     * @param entityDefinitionId the id of the definition to un-publish
     * @return a {@link CompletableFuture} that completes when the definition has been un-published
     */
    CompletableFuture<Void> unPublish(String entityDefinitionId);

    /**
     * Saves the given {@link EntityDefinition} with {@code refresh=wait_for} semantics,
     * guaranteeing the write is searchable before the future completes.
     *
     * @param entity the definition to save
     * @return a {@link CompletableFuture} emitting the saved definition
     */
    CompletableFuture<EntityDefinition> saveSync(EntityDefinition entity);

}
