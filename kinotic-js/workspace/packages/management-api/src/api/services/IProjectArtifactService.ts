import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { ProjectArtifacts } from '@/api/model/ProjectArtifacts'

/**
 * Records the artifacts a project's deployment workloads find, on the project's
 * ProjectDeployment. Every call is authorized against the machine identities the deployment
 * recorded for the project, so only a workload the deployment issued credentials to can
 * report on the project's behalf.
 */
export interface IProjectArtifactService {

    /**
     * Records the artifacts the sync workload found in the checkout of the given commit,
     * replacing what an earlier sync reported. The caller must be the project's sync machine
     * identity.
     * @param projectId the project whose checkout was synced
     * @param commitSha full 40-character SHA of the synced commit
     * @param artifacts the artifacts found; every name must be a single zone label, unique
     *                  among the artifacts of its kind
     * @return Promise resolving once the deployment record holds the artifacts
     */
    recordArtifacts(projectId: string, commitSha: string, artifacts: ProjectArtifacts): Promise<void>

}

export class ProjectArtifactService implements IProjectArtifactService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.ProjectArtifactService`)
    }

    public recordArtifacts(projectId: string, commitSha: string, artifacts: ProjectArtifacts): Promise<void> {
        return this.serviceProxy.invoke('recordArtifacts', [projectId, commitSha, artifacts])
    }

}
