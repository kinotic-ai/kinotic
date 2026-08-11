/** Mirrors the server's {@code org.kinotic.domain.api.model.cluster.KinoticNodeInfo}. */
export interface KinoticNodeInfo {
    nodeId: string
    order: number
    local: boolean
    version: string
}

/** Mirrors the server's {@code org.kinotic.domain.api.model.cluster.KinoticClusterInfo}. */
export interface KinoticClusterInfo {
    localNodeId: string
    serverNodeCount: number
    topologyVersion: number
    clusterState: string
    nodes: KinoticNodeInfo[]
    active: boolean
}
