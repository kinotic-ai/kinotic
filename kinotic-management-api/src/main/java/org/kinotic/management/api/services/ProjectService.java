package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.services.ApplicationScopedCrudService;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.idl.api.annotations.McpTool;

import java.util.List;

/**
 * CRUD service for {@link Project} entities, with application-scoped queries
 * inherited from {@link ApplicationScopedCrudService}.
 */
@Publish
@McpTool
public interface ProjectService extends ApplicationScopedCrudService<Project, String> {

    /**
     * Creates a new project if it does not already exist. If a project with the same id
     * is already present, returns the existing project without modification.
     *
     * @param project the project to create; the id is auto-derived from the application id
     *                and slugified name if not set
     * @return a {@link Future} emitting the created or existing project
     */
    Future<Project> createProjectIfNotExist(Project project);

    /**
     * Looks up projects in the current participant's organization whose backing GitHub repo
     * has the given {@code owner/repo} full name. Returns the empty list when no project in
     * that organization is backed by the repo.
     */
    @McpTool(title = "Find by GitHub Repo", readOnlyHint = true)
    Future<List<Project>> findByRepoFullName(String repoFullName);

    /**
     * Finds the deployment record of the given project in the current participant's
     * organization.
     *
     * @param projectId id of the project the deployment belongs to
     * @return a {@link Future} emitting the deployment record, or {@code null} when the
     *         project has never been deployed
     */
    Future<ProjectDeployment> findDeployment(String projectId);

    /**
     * Re-runs repository initialization for a project left
     * {@link org.kinotic.domain.api.model.RepositoryConnectionStatus#INITIALIZATION_FAILED}
     * by creation, persisting the result. Succeeds with the project marked
     * {@link org.kinotic.domain.api.model.RepositoryConnectionStatus#CONNECTED} once the
     * baseline is committed.
     *
     * @param projectId id of the project to retry
     * @return a {@link Future} emitting the updated project
     * @throws IllegalStateException when the project is not awaiting an initialization retry
     */
    @McpTool(openWorldHint = true)
    Future<Project> retryRepoInitialization(String projectId);

}
