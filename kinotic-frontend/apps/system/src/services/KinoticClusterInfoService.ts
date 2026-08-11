import { Kinotic } from '@kinotic-ai/core'
import { SYSTEM_ZONE } from '@kinotic-ai/os-api'
import type { KinoticClusterInfo } from '@/domain/ClusterInfo'

/**
 * Queries the platform cluster state from the server's {@code KinoticClusterInfoService}
 * (published in the system zone). App-local proxy: the console is its only consumer.
 */
export class KinoticClusterInfoService {

    public getClusterInfo(): Promise<KinoticClusterInfo> {
        return Kinotic.serviceProxy(`${SYSTEM_ZONE}~org.kinotic.os.api.services.KinoticClusterInfoService`)
                      .invoke('getClusterInfo')
    }
}

export const CLUSTER_INFO_SERVICE = new KinoticClusterInfoService()
