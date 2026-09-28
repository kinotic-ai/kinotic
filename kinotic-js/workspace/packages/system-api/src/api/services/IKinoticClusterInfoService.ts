import { SYSTEM_API_ZONE } from '@kinotic-ai/management-api'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import { KinoticClusterInfo } from '@/api/model/cluster/KinoticClusterInfo'

/**
 * Queries the topology and state of the cluster the org, system and app servers form.
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
