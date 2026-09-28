/**
 * A single server node in the cluster, of the org, system or app server. Mirrors the server's
 * {@code org.kinotic.system.api.model.cluster.KinoticNodeInfo}.
 */
export class KinoticNodeInfo {
    public nodeId: string = ''
    public order: number = 0
    public local: boolean = false
    public addresses: string[] = []
    public hostNames: string[] = []
    public version: string = ''
}
