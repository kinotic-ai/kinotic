import { SYSTEM_API_ZONE,
         type MachineParticipantIdentity,
         type MachineProvisionResult,
         type UserParticipantIdentity } from '@kinotic-ai/management-api'
import type { IKinotic, IServiceProxy, Page, Pageable } from '@kinotic-ai/core'

/**
 * The platform's own members: the operators who sign in to the system console, and the
 * SYSTEM-scope machines that connect as platform daemons, such as a worker node's vm-manager.
 * Published in the system zone, which only SYSTEM participants may address; every method works
 * on SYSTEM scope alone, so an organization's users and machines stay that organization's to
 * manage through IMachineService.
 */
export interface ISystemMemberService {

    findUsers(pageable: Pageable): Promise<Page<UserParticipantIdentity>>

    searchUsers(searchText: string, pageable: Pageable): Promise<Page<UserParticipantIdentity>>

    /** Lists the platform's machines, disabled ones included. */
    findMachines(pageable: Pageable): Promise<Page<MachineParticipantIdentity>>

    /**
     * Provisions a platform machine and returns it together with its generated client secret.
     * The secret is disclosed exactly once — it cannot be retrieved later.
     */
    createMachine(displayName: string): Promise<MachineProvisionResult>

    /**
     * Permanently removes a platform machine, including its stored credential. A removed
     * machine's id cannot authenticate again.
     */
    removeMachine(machineId: string): Promise<void>

}

export class SystemMemberService implements ISystemMemberService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${SYSTEM_API_ZONE}~org.kinotic.system.api.services.SystemMemberService`)
    }

    public findUsers(pageable: Pageable): Promise<Page<UserParticipantIdentity>> {
        return this.serviceProxy.invoke('findUsers', [pageable])
    }

    public searchUsers(searchText: string, pageable: Pageable): Promise<Page<UserParticipantIdentity>> {
        return this.serviceProxy.invoke('searchUsers', [searchText, pageable])
    }

    public findMachines(pageable: Pageable): Promise<Page<MachineParticipantIdentity>> {
        return this.serviceProxy.invoke('findMachines', [pageable])
    }

    public createMachine(displayName: string): Promise<MachineProvisionResult> {
        return this.serviceProxy.invoke('createMachine', [displayName])
    }

    public removeMachine(machineId: string): Promise<void> {
        return this.serviceProxy.invoke('removeMachine', [machineId])
    }

}
