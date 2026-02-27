
package org.kinotic.persistence.api.services;

import org.kinotic.domain.api.services.crud.IdentifiableCrudService;
import org.kinotic.domain.api.services.crud.Page;
import org.kinotic.domain.api.services.crud.Pageable;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.core.api.annotations.Publish;

import java.util.concurrent.CompletableFuture;

@Publish
public interface EntityDefinitionService extends IdentifiableCrudService<EntityDefinition, String> {

    /**
     * Counts all {@link EntityDefinition}s for the given application.
     * @param applicationId the application to find {@link EntityDefinition}s for
     * @return a future that will complete with a page of {@link EntityDefinition}s
     */
    CompletableFuture<Long> countForApplication(String applicationId);

    /**
     * Counts all {@link EntityDefinition}s for the given project.
     * @param projectId the project to find {@link EntityDefinition}s for
     * @return a future that will complete with a page of {@link EntityDefinition}s
     */
    CompletableFuture<Long> countForProject(String projectId);

    /**
     * Finds all published {@link EntityDefinition}s for the given application.
     * @param applicationId the application to find {@link EntityDefinition}s for
     * @param pageable the page to return
     * @return a future that will complete with a page of {@link EntityDefinition}s
     */
    CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId, Pageable pageable);

    /**
     * Finds all {@link EntityDefinition}s for the given application.
     * @param applicationId the application to find {@link EntityDefinition}s for
     * @param pageable the page to return
     * @return a future that will complete with a page of {@link EntityDefinition}s
     */
    CompletableFuture<Page<EntityDefinition>> findAllForApplication(String applicationId, Pageable pageable);

    /**
     * Finds all {@link EntityDefinition}s for the given project.
     * @param projectId the project to find {@link EntityDefinition}s for
     * @param pageable the page to return
     * @return a future that will complete with a page of {@link EntityDefinition}s
     */
    CompletableFuture<Page<EntityDefinition>> findAllForProject(String projectId, Pageable pageable);

    /**
     * Publishes thed {@link EntityDefinition} with the given id.
     * This will make the {@link EntityDefinition} available for use to read and write items for.
     * @param entityDefinitionId the id of the {@link EntityDefinition} to publish
     * @return a future that will complete when the {@link EntityDefinition} has been published
     */
    CompletableFuture<Void> publish(String entityDefinitionId);

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a future that will complete when the index has been synced
     */
    CompletableFuture<Void> syncIndex();

    /**
     * Un-publish the {@link EntityDefinition} with the given id.
     * @param entityDefinitionId the id of the {@link EntityDefinition} to un-publish
     * @return a future that will complete when the {@link EntityDefinition} has been unpublished
     */
    CompletableFuture<Void> unPublish(String entityDefinitionId);

}
