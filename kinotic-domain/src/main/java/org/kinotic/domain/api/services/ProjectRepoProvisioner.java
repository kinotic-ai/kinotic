package org.kinotic.domain.api.services;

import org.kinotic.domain.api.model.Project;

import java.util.concurrent.CompletableFuture;

/**
 * Provisions the backing source-control repository for a {@link Project} during
 * creation.
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
