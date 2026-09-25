import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { UiDeployment } from '@/api/model/UiDeployment'

/**
 * The UI deployments of the caller's organization's projects, as the console shows and acts
 * on them: the sites serving each project's UIs. Removal is the one path that takes a site
 * down and deletes its files, carried out by the deployment's worker; a deployment whose UI a commit dropped stays orphaned, still
 * serving, until it is removed here.
 */
export interface IUiDeploymentService {

    /**
     * Lists the UI deployments of one of the caller's organization's projects, ordered by UI
     * name. A project that has never published a UI has none.
     * @param projectId a project belonging to the caller's organization
     */
    findAllForProject(projectId: string): Promise<UiDeployment[]>

    /**
     * Asks for the deployment's removal: its worker takes the site down, deletes the UI's
     * published files, and deletes the record. A UI the project's current commit still contains
     * is published again, at a site minted anew, by the next deployment.
     * @param deploymentId the deployment of a UI of one of the caller's organization's projects
     */
    remove(deploymentId: string): Promise<void>

}

export class UiDeploymentService implements IUiDeploymentService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.UiDeploymentService`)
    }

    public findAllForProject(projectId: string): Promise<UiDeployment[]> {
        return this.serviceProxy.invoke('findAllForProject', [projectId])
    }

    public remove(deploymentId: string): Promise<void> {
        return this.serviceProxy.invoke('remove', [deploymentId])
    }

}
