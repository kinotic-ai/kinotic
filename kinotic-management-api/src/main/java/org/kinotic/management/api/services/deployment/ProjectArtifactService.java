package org.kinotic.management.api.services.deployment;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.deployment.ProjectArtifacts;
import org.kinotic.management.api.model.deployment.ProjectDeployment;

/**
 * Records the artifacts a project's deployment workloads find, and the SBOM they generate, on the
 * project's {@link ProjectDeployment}. Every call is authorized against the machine identities the
 * deployment recorded for the project, so only a workload the deployment issued credentials to can
 * report on the project's behalf.
 */
@Publish
public interface ProjectArtifactService {

    /**
     * Records the artifacts the sync workload found in the checkout of a commit, replacing what an
     * earlier sync reported. The caller must be the project's sync machine identity.
     *
     * @param projectId the project whose checkout was synced
     * @param artifacts the artifacts found, with the full 40-character SHA of the synced commit;
     *                  every name must be a single zone label, unique among the artifacts of its kind
     * @return a future completing once the deployment record holds the artifacts
     */
    Future<Void> recordArtifacts(String projectId, ProjectArtifacts artifacts);

    /**
     * Records the SBOM the SBOM workload generated from the project's checkout and uploaded to the
     * organization's storage. The caller must be the project's sync machine identity, and the
     * dependency hash the one the sync workload last reported.
     *
     * @param projectId      the project whose checkout the SBOM was generated from
     * @param dependencyHash the fingerprint of the dependencies the document lists
     * @return a future completing once the deployment record holds the SBOM
     */
    Future<Void> recordSbom(String projectId, String dependencyHash);

}
