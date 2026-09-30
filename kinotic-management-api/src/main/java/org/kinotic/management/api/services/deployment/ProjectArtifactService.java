package org.kinotic.management.api.services.deployment;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.deployment.ProjectArtifacts;
import org.kinotic.management.api.model.deployment.ProjectDependencies;
import org.kinotic.management.api.model.deployment.ProjectDeployment;

/**
 * Records the artifacts a project's deployment workloads find on the project's
 * {@link ProjectDeployment}, and the SBOM they generate as its {@link ProjectDependencies}. Every
 * call is authorized by the caller's organization alone, so any participant of the project's
 * organization can report on the project's behalf.
 */
@Publish
public interface ProjectArtifactService {

    /**
     * Records the artifacts the sync workload found in the checkout of a commit, replacing what an
     * earlier sync reported.
     *
     * @param projectId the project whose checkout was synced
     * @param artifacts the artifacts found, with the full 40-character SHA of the synced commit;
     *                  every name must be a single zone label, unique among the artifacts of its kind,
     *                  and a UI's name must not contain {@code --}
     * @return a future completing once the deployment record holds the artifacts
     */
    Future<Void> recordArtifacts(String projectId, ProjectArtifacts artifacts);

    /**
     * Records the SBOM the SBOM workload generated from the project's checkout, replacing the one an
     * earlier run recorded. The dependency hash must be the one the sync workload last reported.
     *
     * @param projectId      the project whose checkout the SBOM was generated from
     * @param dependencyHash the fingerprint of the dependencies the tree lists
     * @param dependencies   the dependency tree, stored under the project's id, organization and
     *                       application whatever ids it carries; no package may be listed twice,
     *                       and every position must name one of its packages
     * @return a future completing once the tree is stored and the deployment record says the
     *         project has an SBOM of its dependencies
     */
    Future<Void> recordSbom(String projectId, String dependencyHash, ProjectDependencies dependencies);

}
