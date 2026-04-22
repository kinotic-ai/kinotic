package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.ApplicationScopedCrudService;
import org.kinotic.os.api.model.Project;

import java.util.concurrent.CompletableFuture;

/**
 * CRUD service for {@link Project} entities, with application-scoped queries
 * inherited from {@link ApplicationScopedCrudService}.
 */
@Publish
public interface ProjectService extends ApplicationScopedCrudService<Project, String> {

    /**
     * Creates a new project if it does not already exist. If a project with the same id
     * is already present, returns the existing project without modification.
     *
     * @param project the project to create; the id is auto-derived from the application id
     *                and slugified name if not set
     * @return a {@link CompletableFuture} emitting the created or existing project
     */
    CompletableFuture<Project> createProjectIfNotExist(Project project);

}
