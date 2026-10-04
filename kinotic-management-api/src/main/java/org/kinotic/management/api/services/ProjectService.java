package org.kinotic.management.api.services;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;
import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchEventKind;
import org.kinotic.domain.api.services.ApplicationScopedCrudService;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.deployment.ProjectDependencies;
import org.kinotic.management.api.model.deployment.ProjectDeployment;
import org.kinotic.idl.api.annotations.McpTool;

import java.util.List;

/**
 * CRUD service for {@link Project} entities, with application-scoped queries
 * inherited from {@link ApplicationScopedCrudService}.
 */
@Publish
@McpTool
@AuthzResource(value = ProjectService.RESOURCE_TYPE, parent = AuthzUtil.APPLICATION_TYPE)
public interface ProjectService extends ApplicationScopedCrudService<Project, String> {

    /** The resource type a project is in the authorization model. */
    String RESOURCE_TYPE = "project";

    /**
     * Returns the number of projects the caller may see: those the caller may view, and those containing an
     * entity definition the caller may view.
     *
     * @return {@link Future} emitting the number of projects
     */
    @Override
    @AuthzCheck(zoneOnly = true)
    Future<Long> count();

    /**
     * Returns a {@link Page} of the projects the caller may see: those the caller may view, and those containing
     * an entity definition the caller may view.
     *
     * @param pageable the page settings to be used
     * @return a page of projects
     */
    @Override
    @AuthzCheck(zoneOnly = true)
    Future<Page<Project>> findAll(Pageable pageable);

    /**
     * Returns a {@link Page} of the projects matching the search text among those the caller may see, as
     * {@link #findAll(Pageable)} lists them.
     *
     * @param searchText the text to search for projects for
     * @param pageable   the page settings to be used
     * @return a page of projects
     */
    @Override
    @AuthzCheck(zoneOnly = true)
    Future<Page<Project>> search(String searchText, Pageable pageable);

    /**
     * Returns the number of the application's projects the caller may see, as {@link #findAll(Pageable)} lists
     * them.
     *
     * @param applicationId the application's id
     * @return {@link Future} emitting the number of projects
     */
    @Override
    @AuthzCheck(zoneOnly = true)
    Future<Long> countForApplication(String applicationId);

    /**
     * Returns a {@link Page} of the application's projects the caller may see, as {@link #findAll(Pageable)}
     * lists them.
     *
     * @param applicationId the application's id
     * @param pageable      the page settings to be used
     * @return a page of projects
     */
    @Override
    @AuthzCheck(zoneOnly = true)
    Future<Page<Project>> findAllForApplication(String applicationId, Pageable pageable);

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
     * Lists what happened to the deployment of the given project in the current participant's
     * organization and to the records it made, the deployment jobs, build VMs, microservice
     * deployments and UI deployments, newest first, with what caused each.
     *
     * @param projectId id of the project the deployment belongs to
     * @param pageable  the page to return
     * @return a {@link Future} emitting a page of ledger entries, empty when the project has never
     *         been deployed
     */
    Future<Page<WatchEvent>> findDeploymentHistory(String projectId, Pageable pageable);

    /**
     * Lists what happened to the deployments of every project in the current participant's
     * organization and to the records they made, newest first, with what caused each: the entries
     * {@link #findDeploymentHistory(String, Pageable)} lists for each project, limited to the given
     * kinds.
     *
     * @param kinds    the kinds of entries to list
     * @param pageable the page to return
     * @return a {@link Future} emitting a page of ledger entries
     */
    Future<Page<WatchEvent>> findAllDeploymentHistory(List<WatchEventKind> kinds, Pageable pageable);

    /**
     * Finds the SBOM of the given project in the current participant's organization: every
     * package version its lockfile installs, how the project reaches each, and which package
     * depends on which.
     *
     * @param projectId id of the project the SBOM belongs to
     * @return a {@link Future} emitting the dependency tree, or {@code null} when the project has
     *         no SBOM of the dependencies its last sync reported
     */
    Future<ProjectDependencies> findDependencies(String projectId);

    /**
     * Re-runs repository initialization for a project left
     * {@link org.kinotic.management.api.model.RepositoryConnectionStatus#INITIALIZATION_FAILED}
     * by creation, persisting the result. Succeeds with the project marked
     * {@link org.kinotic.management.api.model.RepositoryConnectionStatus#CONNECTED} once the
     * baseline is committed.
     *
     * @param projectId id of the project to retry
     * @return a {@link Future} emitting the updated project
     * @throws IllegalStateException when the project is not awaiting an initialization retry
     */
    @McpTool(openWorldHint = true)
    @AuthzCheck(permission = AuthzUtil.CAN_EDIT)
    Future<Project> retryRepoInitialization(String projectId);

}
