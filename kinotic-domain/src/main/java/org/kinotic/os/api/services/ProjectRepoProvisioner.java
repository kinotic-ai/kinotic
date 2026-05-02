package org.kinotic.os.api.services;

import org.kinotic.os.api.model.Project;

import java.util.concurrent.CompletableFuture;

/**
 * Provisions the backing source-control repository for a {@link Project} during
 * creation. The implementation in {@code kinotic-github} creates a GitHub repo
 * from the configured template and stamps the repo metadata
 * ({@code repoFullName}, {@code repoId}, {@code defaultBranch}) on the project
 * before it is persisted. The caller-supplied {@code repoPrivate} flag is passed
 * through to GitHub at create time.
 * <p>
 * Implementations should fail fast when the org is not yet linked to GitHub —
 * the caller surfaces this to the user as "link GitHub before creating a project".
 */
public interface ProjectRepoProvisioner {

    /**
     * Creates the backing repository for {@code project} and stamps the resulting
     * repo metadata on the same instance. Returns the project unchanged on success.
     *
     * @throws IllegalStateException when prerequisites aren't met (e.g. GitHub not linked)
     */
    CompletableFuture<Project> provision(Project project);
}
