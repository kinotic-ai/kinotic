import { SYSTEM_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy, Page, Pageable } from '@kinotic-ai/core'
import { ServiceDirectoryEntry } from '@/api/model/ServiceDirectoryEntry'

/**
 * The service directory for platform operators: every registered service contract with its
 * verified liveness, plus the on-demand liveness corrections. Published in the system zone,
 * which only SYSTEM participants may address.
 */
export interface ISystemServiceDirectoryService {

    findEntries(pageable: Pageable): Promise<Page<ServiceDirectoryEntry>>

    verifyLiveness(serviceAddress: string): Promise<void>

    reconcileLiveness(): Promise<void>

}

export class SystemServiceDirectoryService implements ISystemServiceDirectoryService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${SYSTEM_ZONE}~org.kinotic.os.api.services.SystemServiceDirectoryService`)
    }

    public findEntries(pageable: Pageable): Promise<Page<ServiceDirectoryEntry>> {
        return this.serviceProxy.invoke('findEntries', [pageable])
    }

    public verifyLiveness(serviceAddress: string): Promise<void> {
        return this.serviceProxy.invoke('verifyLiveness', [serviceAddress])
    }

    public reconcileLiveness(): Promise<void> {
        return this.serviceProxy.invoke('reconcileLiveness')
    }

}
