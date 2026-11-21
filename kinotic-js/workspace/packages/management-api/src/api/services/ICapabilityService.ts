import { MANAGEMENT_API_ZONE } from '@/api/Zones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { Capabilities } from '@/api/model/Capabilities'

/**
 * Tells a UI which optional features this Kinotic server offers.
 */
export interface ICapabilityService {

    /**
     * Finds the optional features this server offers.
     */
    findCapabilities(): Promise<Capabilities>

}

export class CapabilityService implements ICapabilityService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.CapabilityService`)
    }

    public findCapabilities(): Promise<Capabilities> {
        return this.serviceProxy.invoke('findCapabilities', [])
    }

}
