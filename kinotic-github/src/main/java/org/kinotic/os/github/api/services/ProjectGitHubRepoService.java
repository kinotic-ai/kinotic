package org.kinotic.os.github.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.os.github.api.model.AvailableRepo;
import org.kinotic.os.github.api.model.ProjectGitHubRepoLink;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service the project-settings UI uses to read and mutate the link between a Kinotic
 * Project and a GitHub repository. Links are 1:1 — at most one repo per project.
 */
@Publish
public interface ProjectGitHubRepoService extends IdentifiableCrudService<ProjectGitHubRepoLink, String> {

    /**
     * Lists the repositories visible under the caller's organization's installation,
     * for populating the repo dropdown in the link UI. Calls GitHub on every invocation;
     * not cached because the org admin may have just toggled repo access.
     */
    CompletableFuture<List<AvailableRepo>> listAvailableRepos();

    /**
     * Creates or replaces the link for the given project. Validates that the chosen
     * {@code repoFullName} is reachable through the org's installation before persisting.
     */
    CompletableFuture<ProjectGitHubRepoLink> linkProject(String projectId, String repoFullName);

    /** Removes the link for the given project, if any. */
    CompletableFuture<Void> unlinkProject(String projectId);

    /**
     * Returns the link for the given project, or {@code null} when none exists.
     */
    CompletableFuture<ProjectGitHubRepoLink> findByProject(String projectId);
}
