/**
 * A single kinotic-server node in the cluster. Mirrors the server's
 * {@code org.kinotic.domain.api.model.cluster.KinoticNodeInfo}.
 */
export class KinoticNodeInfo {
    public nodeId: string = ''
    public order: number = 0
    public local: boolean = false
    public addresses: string[] = []
    public hostNames: string[] = []
    public version: string = ''
}
