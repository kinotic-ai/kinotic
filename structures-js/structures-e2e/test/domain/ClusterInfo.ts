export class ClusterInfo {
    public localNodeId: string;
    public serverNodeCount: number;
    public topologyVersion: number;
    public clusterState: string;
    public nodes: NodeInfo[];
    public active: boolean;
}

export class NodeInfo {
    public nodeId: string;
    public order: number;
    public local: boolean;
    public addresses: string[];
    public hostNames: string[];
    public client: boolean;
    public attributes: Record<string, any>;
    public version: string;
}