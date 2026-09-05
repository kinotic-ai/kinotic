package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.ProjectArtifacts;
import org.kinotic.management.api.model.ProjectDeployment;

/**
 * Records the artifacts a project's deployment workloads find, on the project's
 * {@link ProjectDeployment}. Every call is authorized against the machine identities the
 * deployment recorded for the project, so only a workload the deployment issued credentials
 * to can report on the project's behalf.
 */
@Publish
public interface ProjectArtifactService {

    /**
     * Records the artifacts the sync workload found in the checkout of the given commit,
     * replacing what an earlier sync reported. The caller must be the project's sync machine
     * identity.
     *
     * @param projectId the project whose checkout was synced
     * @param commitSha full 40-character SHA of the synced commit
     * @param artifacts the artifacts found; every name must be a single zone label, unique
     *                  among the artifacts of its kind
     * @return a future completing once the deployment record holds the artifacts
     */
    Future<Void> recordArtifacts(String projectId, String commitSha, ProjectArtifacts artifacts);

}
