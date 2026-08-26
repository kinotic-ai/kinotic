import { SYSTEM_API_ZONE } from '@/api/SystemZone'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import { KinoticClusterInfo } from '@/api/model/KinoticClusterInfo'

/**
 * Queries the kinotic-server cluster's topology and state.
 */
export interface IKinoticClusterInfoService {

    /**
     * @return the cluster's current {@link KinoticClusterInfo}
     */
    getClusterInfo(): Promise<KinoticClusterInfo>

}

export class KinoticClusterInfoService implements IKinoticClusterInfoService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${SYSTEM_API_ZONE}~org.kinotic.system.api.services.KinoticClusterInfoService`)
    }

    public getClusterInfo(): Promise<KinoticClusterInfo> {
        return this.serviceProxy.invoke('getClusterInfo')
    }

}
