export class ClusterInfo {
    public localNodeId: string = '';
    public serverNodeCount: number = 0;
    public topologyVersion: number = 0;
    public clusterState: string = '';
    public nodes: NodeInfo[] = [];
    public active: boolean = false;
}

export class NodeInfo {
    public nodeId: string = '';
    public order: number = 0;
    public local: boolean = false;
    public addresses: string[] = [];
    public hostNames: string[] = [];
    public client: boolean = false;
    public attributes: Record<string, any> = {};
    public version: string = '';
}
