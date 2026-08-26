import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { ProjectDeployment } from '@/api/model/ProjectDeployment'

/**
 * Read access to ProjectDeployment records for the current participant's organization, so
 * the portal can follow where a project is deployed and how its latest deployment went.
 */
export interface IProjectDeploymentService {

    /**
     * Finds the deployment record of the given project in the current participant's
     * organization.
     * @param projectId id of the project the deployment belongs to
     * @return Promise emitting the deployment record, or null when the project has never
     *         been deployed
     */
    findByProjectId(projectId: string): Promise<ProjectDeployment | null>

}

export class ProjectDeploymentService implements IProjectDeploymentService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.ProjectDeploymentService`)
    }

    public findByProjectId(projectId: string): Promise<ProjectDeployment | null> {
        return this.serviceProxy.invoke('findByProjectId', [projectId])
    }

}
