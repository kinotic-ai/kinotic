package org.kinotic.persistence.api.services;

import java.util.concurrent.CompletableFuture;

import org.kinotic.continuum.api.annotations.Proxy;
import org.kinotic.continuum.api.annotations.Publish;
import org.kinotic.continuum.api.crud.IdentifiableCrudService;
import org.kinotic.persistence.api.domain.Project;
import org.kinotic.continuum.api.crud.Page;
import org.kinotic.continuum.api.crud.Pageable;

@Publish
@Proxy
public interface ProjectService extends IdentifiableCrudService<Project, String> {

    /**
     * Counts all projects for the given application.
     * @param applicationId the application to find projects for
     * @return a future that will complete with the number of projects
     */
    CompletableFuture<Long> countForApplication(String applicationId);

    /**
     * Creates a new project if it does not already exist.
     * @param project the project to create
     * @return {@link CompletableFuture} emitting the created project or the existing project if it already exists
     */
    CompletableFuture<Project> createProjectIfNotExist(Project project);

    /**
     * Finds all projects for the given application.
     * @param applicationId the application to find projects for
     * @param pageable the page to return
     * @return a future that will complete with a page of projects
     */
    CompletableFuture<Page<Project>> findAllForApplication(String applicationId, Pageable pageable);

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a future that will complete when the index has been synced
     */
    CompletableFuture<Void> syncIndex();

}
