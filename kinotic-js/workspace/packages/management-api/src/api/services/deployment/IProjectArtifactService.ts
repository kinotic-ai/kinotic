import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { ProjectArtifacts } from '@/api/model/deployment/ProjectArtifacts'

/**
 * Records the artifacts a project's deployment workloads find, and the SBOM they generate, on the
 * project's ProjectDeployment. Every call is authorized against the machine identities the
 * deployment recorded for the project, so only a workload the deployment issued credentials to can
 * report on the project's behalf.
 */
export interface IProjectArtifactService {

    /**
     * Records the artifacts the sync workload found in the checkout of a commit, replacing what an
     * earlier sync reported. The caller must be the project's sync machine identity.
     * @param projectId the project whose checkout was synced
     * @param artifacts the artifacts found, with the full 40-character SHA of the synced commit;
     *                  every name must be a single zone label, unique among the artifacts of its kind
     * @return Promise resolving once the deployment record holds the artifacts
     */
    recordArtifacts(projectId: string, artifacts: ProjectArtifacts): Promise<void>

    /**
     * Records the SBOM the SBOM workload generated from the project's checkout and uploaded to the
     * organization's storage. The caller must be the project's sync machine identity, and the
     * dependency hash the one the sync workload last reported.
     * @param projectId the project whose checkout the SBOM was generated from
     * @param dependencyHash the fingerprint of the dependencies the document lists
     * @return Promise resolving once the deployment record holds the SBOM
     */
    recordSbom(projectId: string, dependencyHash: string): Promise<void>

}

export class ProjectArtifactService implements IProjectArtifactService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.deployment.ProjectArtifactService`)
    }

    public recordArtifacts(projectId: string, artifacts: ProjectArtifacts): Promise<void> {
        return this.serviceProxy.invoke('recordArtifacts', [projectId, artifacts])
    }

    public recordSbom(projectId: string, dependencyHash: string): Promise<void> {
        return this.serviceProxy.invoke('recordSbom', [projectId, dependencyHash])
    }

}
