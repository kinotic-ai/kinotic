import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { MicroserviceDeployment } from '@/api/model/MicroserviceDeployment'

/**
 * The microservice deployments of the caller's organization's projects, as the console shows
 * and acts on them. Removal is the one path that destroys a microservice's VM and identity; a
 * deployment whose microservice a commit dropped stays orphaned until it is removed here.
 */
export interface IMicroserviceDeploymentService {

    /**
     * Lists the microservice deployments of one of the caller's organization's projects,
     * ordered by microservice name. A project that has never deployed has none.
     * @param projectId a project belonging to the caller's organization
     */
    findAllForProject(projectId: string): Promise<MicroserviceDeployment[]>

    /**
     * Restarts the microservice's VM in place: a running VM is stopped and booted again with its
     * disk intact, a stopped or failed one is booted again. Fails when the deployment has no VM,
     * which the next deployment of the project resolves.
     * @param deploymentId the deployment of a microservice of one of the caller's organization's projects
     */
    restart(deploymentId: string): Promise<MicroserviceDeployment>

    /**
     * Removes the deployment: destroys the microservice's VM, removes its machine identity, and
     * deletes the record. A microservice the project's current commit still contains is deployed
     * again by the next deployment.
     * @param deploymentId the deployment of a microservice of one of the caller's organization's projects
     */
    remove(deploymentId: string): Promise<void>

}

export class MicroserviceDeploymentService implements IMicroserviceDeploymentService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.MicroserviceDeploymentService`)
    }

    public findAllForProject(projectId: string): Promise<MicroserviceDeployment[]> {
        return this.serviceProxy.invoke('findAllForProject', [projectId])
    }

    public restart(deploymentId: string): Promise<MicroserviceDeployment> {
        return this.serviceProxy.invoke('restart', [deploymentId])
    }

    public remove(deploymentId: string): Promise<void> {
        return this.serviceProxy.invoke('remove', [deploymentId])
    }

}
