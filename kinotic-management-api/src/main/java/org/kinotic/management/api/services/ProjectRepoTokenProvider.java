package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.management.api.model.ProjectRepoToken;

/**
 * Mints short-lived read credentials for a project's backing repository. Implemented by the
 * platform's repository host integration and consumed in-process by deployment flows that
 * check a project's code out onto a worker node.
 * <p>
 * Callers are trusted server-side code and pass the {@code organizationId} explicitly;
 * implementations validate that the project belongs to it.
 */
public interface ProjectRepoTokenProvider {

    /**
     * Issues a token authorizing read access to the backing repository of the given project.
     *
     * @param organizationId the organization the project must belong to
     * @param projectId      the project whose repository the token is for
     * @return a future completing with the token and its clone metadata
     */
    Future<ProjectRepoToken> issueRepoToken(String organizationId, String projectId);

}
