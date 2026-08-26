import { KinoticNodeInfo } from '@/api/model/KinoticNodeInfo'

/**
 * The kinotic-server cluster's current topology and state. Mirrors the server's
 * {@code org.kinotic.domain.api.model.cluster.KinoticClusterInfo}.
 */
export class KinoticClusterInfo {
    public localNodeId: string = ''
    public serverNodeCount: number = 0
    public topologyVersion: number = 0
    public clusterState: string = ''
    public nodes: KinoticNodeInfo[] = []
    public active: boolean = false
}
