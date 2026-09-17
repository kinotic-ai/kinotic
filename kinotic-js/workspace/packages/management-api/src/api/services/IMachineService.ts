import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy, IterablePage, Page, Pageable } from '@kinotic-ai/core'
import { FunctionalIterablePage } from '@kinotic-ai/core'
import type { MachineParticipantIdentity } from '@/api/model/security/MachineParticipantIdentity'
import type { MachineProvisionResult } from '@/api/model/security/MachineProvisionResult'

/**
 * Machine-identity management for the applications and projects of the caller's organization.
 * A machine is a non-human caller that connects through the Kinotic client with the identity's
 * id as clientId and a secret issued here. An API client of one application acts with that
 * application's scope; the machines a project's deployment provisions for its workloads act
 * with the organization's scope. The application or project must belong to the caller's
 * organization, and only its machines are visible or mutable.
 */
export interface IMachineService {

    /**
     * Provisions a machine for an application of the caller's organization and returns it
     * together with its generated client secret. The secret is disclosed exactly once — it
     * cannot be retrieved later, only rotated.
     */
    createMachine(displayName: string, applicationId: string): Promise<MachineProvisionResult>

    /** Lists the machines of the given application of the caller's organization, disabled ones included. */
    findMachines(applicationId: string, pageable: Pageable): Promise<IterablePage<MachineParticipantIdentity>>

    /**
     * Lists the machines the deployment of one of the caller's organization's projects has
     * provisioned for its workloads, in the order the deployment records them — the sync
     * workload's, then one per microservice in name order. A project that has never deployed
     * has none.
     */
    findProjectMachines(projectId: string): Promise<MachineParticipantIdentity[]>

    /**
     * Replaces a machine's client secret, returning the new secret exactly once. The old
     * secret stops working immediately; a connection the machine already holds lasts until
     * it disconnects.
     */
    rotateSecret(machineId: string): Promise<string>

    /**
     * Enables or disables a machine. A disabled machine is cut off on its next connection,
     * and enabling it restores access with the same credential.
     */
    setMachineEnabled(machineId: string, enabled: boolean): Promise<void>

    /**
     * Permanently removes a machine, including its stored credential. A removed machine's id
     * cannot authenticate again.
     */
    removeMachine(machineId: string): Promise<void>

}

export class MachineService implements IMachineService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.security.MachineService`)
    }

    public createMachine(displayName: string, applicationId: string): Promise<MachineProvisionResult> {
        return this.serviceProxy.invoke('createMachine', [displayName, applicationId])
    }

    public async findMachines(applicationId: string, pageable: Pageable): Promise<IterablePage<MachineParticipantIdentity>> {
        const page: Page<MachineParticipantIdentity> = await this.serviceProxy.invoke('findMachines', [applicationId, pageable])
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.serviceProxy.invoke('findMachines', [applicationId, next]))
    }

    public findProjectMachines(projectId: string): Promise<MachineParticipantIdentity[]> {
        return this.serviceProxy.invoke('findProjectMachines', [projectId])
    }

    public rotateSecret(machineId: string): Promise<string> {
        return this.serviceProxy.invoke('rotateSecret', [machineId])
    }

    public setMachineEnabled(machineId: string, enabled: boolean): Promise<void> {
        return this.serviceProxy.invoke('setMachineEnabled', [machineId, enabled])
    }

    public removeMachine(machineId: string): Promise<void> {
        return this.serviceProxy.invoke('removeMachine', [machineId])
    }
}
