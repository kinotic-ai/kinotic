
package org.kinotic.persistence.api.services;

import org.kinotic.core.api.services.crud.IdentifiableCrudService;
import org.kinotic.core.api.services.crud.Page;
import org.kinotic.core.api.services.crud.Pageable;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.rpc.api.annotations.Publish;

import java.util.concurrent.CompletableFuture;

@Publish
public interface StructureService extends IdentifiableCrudService<Structure, String> {

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
     * @param pageable the page to return
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
     * Publishes thed structure with the given id.
     * This will make the structure available for use to read and write items for.
     * @param structureId the id of the structure to publish
     * @return a future that will complete when the structure has been published
     */
    CompletableFuture<Void> publish(String structureId);

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a future that will complete when the index has been synced
     */
    CompletableFuture<Void> syncIndex();

    /**
     * Un-publish the structure with the given id.
     * @param structureId the id of the structure to un-publish
     * @return a future that will complete when the structure has been unpublished
     */
    CompletableFuture<Void> unPublish(String structureId);

}
