import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { ProjectArtifacts } from '@/api/model/deployment/ProjectArtifacts'
import type { ProjectDependencies } from '@/api/model/deployment/ProjectDependencies'

/**
 * Records the artifacts a project's deployment workloads find on the project's ProjectDeployment,
 * and the SBOM they generate as its ProjectDependencies. Every call is authorized by the caller's
 * organization alone, so any participant of the project's organization can report on the
 * project's behalf.
 */
export interface IProjectArtifactService {

    /**
     * Records the artifacts the sync workload found in the checkout of a commit, replacing what an
     * earlier sync reported.
     * @param projectId the project whose checkout was synced
     * @param artifacts the artifacts found, with the full 40-character SHA of the synced commit;
     *                  every name must be a single zone label, unique among the artifacts of its kind
     * @return Promise resolving once the deployment record holds the artifacts
     */
    recordArtifacts(projectId: string, artifacts: ProjectArtifacts): Promise<void>

    /**
     * Records the SBOM the SBOM workload generated from the project's checkout, replacing the one an
     * earlier run recorded. The dependency hash must be the one the sync workload last reported.
     * @param projectId the project whose checkout the SBOM was generated from
     * @param dependencyHash the fingerprint of the dependencies the tree lists
     * @param dependencies the dependency tree, stored under the project's id, organization and
     *                     application whatever ids it carries; no package may be listed twice, and
     *                     every position must name one of its packages
     * @return Promise resolving once the tree is stored and the deployment record says the project
     *         has an SBOM of its dependencies
     */
    recordSbom(projectId: string, dependencyHash: string, dependencies: ProjectDependencies): Promise<void>

}

export class ProjectArtifactService implements IProjectArtifactService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.deployment.ProjectArtifactService`)
    }

    public recordArtifacts(projectId: string, artifacts: ProjectArtifacts): Promise<void> {
        return this.serviceProxy.invoke('recordArtifacts', [projectId, artifacts])
    }

    public recordSbom(projectId: string, dependencyHash: string, dependencies: ProjectDependencies): Promise<void> {
        return this.serviceProxy.invoke('recordSbom', [projectId, dependencyHash, dependencies])
    }

}
