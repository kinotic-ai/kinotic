package org.kinotic.persistence.internal.api.services;

import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.persistence.api.model.EntityDefinition;

import java.util.concurrent.CompletableFuture;

/**
 * Internal DAO for common functionality so we don't have circular references
 * Created by Navíd Mitchell 🤪on 6/25/23.
 */
public interface EntityDefinitionDAO extends IdentifiableCrudService<EntityDefinition, String> {

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
     * @param pageable the page to return«
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

}
