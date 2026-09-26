import { KinoticNodeInfo } from '@/api/model/cluster/KinoticNodeInfo'

/**
 * The current topology and state of the cluster the org, system and app servers form. Mirrors the
 * server's {@code org.kinotic.system.api.model.cluster.KinoticClusterInfo}.
 */
export class KinoticClusterInfo {
    public localNodeId: string = ''
    public serverNodeCount: number = 0
    public topologyVersion: number = 0
    public clusterState: string = ''
    public nodes: KinoticNodeInfo[] = []
    public active: boolean = false
}
