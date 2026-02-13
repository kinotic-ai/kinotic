package org.kinotic.structures.internal.api.services;

import org.kinotic.continuum.core.api.crud.IdentifiableCrudService;
import org.kinotic.continuum.core.api.crud.Page;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.kinotic.structures.api.domain.Structure;

import java.util.concurrent.CompletableFuture;

/**
 * Internal DAO for common functionality so we don't have circular references
 * Created by Navíd Mitchell 🤪on 6/25/23.
 */
public interface StructureDAO extends IdentifiableCrudService<Structure, String> {

    /**
     * Counts all structures for the given application.
     * @param applicationId the application to find structures for
     * @return a future that will complete with a page of structures
     */
    CompletableFuture<Long> countForApplication(String applicationId);

    /**
     * Counts all structures for the given project.
     * @param projectId the project to find structures for
     * @return a future that will complete with a page of structures
     */
    CompletableFuture<Long> countForProject(String projectId);

    /**
     * Finds all published structures for the given application.
     * @param applicationId the application to find structures for
     * @param pageable the page to return
     * @return a future that will complete with a page of structures
     */
    CompletableFuture<Page<Structure>> findAllPublishedForApplication(String applicationId, Pageable pageable);

    /**
     * Finds all structures for the given application.
     * @param applicationId the application to find structures for
     * @param pageable the page to return«
     * @return a future that will complete with a page of structures
     */
    CompletableFuture<Page<Structure>> findAllForApplication(String applicationId, Pageable pageable);

    /**
     * Finds all structures for the given project.
     * @param projectId the project to find structures for
     * @param pageable the page to return
     * @return a future that will complete with a page of structures
     */
    CompletableFuture<Page<Structure>> findAllForProject(String projectId, Pageable pageable);

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a future that will complete when the index has been synced
     */
    CompletableFuture<Void> syncIndex();

}
