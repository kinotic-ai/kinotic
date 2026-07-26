package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.services.ApplicationScopedCrudService;
import org.kinotic.domain.api.model.Project;
import org.kinotic.idl.api.annotations.McpTool;

import java.util.List;
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
    @McpTool(description = "Creates a new project if it does not already exist. If a project with the same id is already present, returns the existing project without modification.")
    CompletableFuture<Project> createProjectIfNotExist(Project project);

    /**
     * Looks up projects in the current participant's organization whose backing GitHub repo
     * has the given {@code owner/repo} full name. Returns the empty list when no project in
     * that organization is backed by the repo.
     */
    @McpTool(title = "Find Projects by GitHub Repo",
             description = "Looks up projects in the current participant's organization whose backing GitHub repo has the given owner/repo full name. Returns the empty list when no project in that organization is backed by the repo.",
             readOnlyHint = true)
    CompletableFuture<List<Project>> findByRepoFullName(String repoFullName);

    /**
     * Re-runs repository initialization for a project left
     * {@link org.kinotic.domain.api.model.RepositoryConnectionStatus#INITIALIZATION_FAILED}
     * by creation, persisting the result. Succeeds with the project marked
     * {@link org.kinotic.domain.api.model.RepositoryConnectionStatus#CONNECTED} once the
     * baseline is committed.
     *
     * @param projectId id of the project to retry
     * @return a {@link CompletableFuture} emitting the updated project
     * @throws IllegalStateException when the project is not awaiting an initialization retry
     */
    @McpTool(description = "Re-runs repository initialization for a project left in INITIALIZATION_FAILED by creation, persisting the result. Succeeds with the project marked CONNECTED once the baseline is committed.")
    CompletableFuture<Project> retryRepoInitialization(String projectId);

}
