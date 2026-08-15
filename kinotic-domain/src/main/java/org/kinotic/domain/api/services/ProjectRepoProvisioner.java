package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.Project;

/**
 * Provisions the backing source-control repository for a {@link Project} during
 * creation.
 */
public interface ProjectRepoProvisioner {

    /**
     * Creates the backing repository for {@code project} and stamps the resulting
     * repo metadata on the same instance. When the repository is created but its
     * initial content cannot be prepared, returns the project carrying
     * {@link org.kinotic.domain.api.model.RepositoryConnectionStatus#INITIALIZATION_FAILED}
     * so the repository is adopted rather than orphaned and initialization can be
     * retried via {@link #reinitialize(Project)}.
     *
     * @throws IllegalStateException when prerequisites aren't met (e.g. GitHub not linked)
     */
    Future<Project> provision(Project project);

    /**
     * Re-runs initialization against the already-created repository named by
     * {@code project}'s stamped repo metadata, stamping
     * {@link org.kinotic.domain.api.model.RepositoryConnectionStatus#CONNECTED} on
     * success. Idempotent: safe to call repeatedly on a project left
     * {@link org.kinotic.domain.api.model.RepositoryConnectionStatus#INITIALIZATION_FAILED}
     * by {@link #provision(Project)}. The returned future fails when initialization
     * fails again, leaving the persisted status unchanged.
     */
    Future<Project> reinitialize(Project project);
}
