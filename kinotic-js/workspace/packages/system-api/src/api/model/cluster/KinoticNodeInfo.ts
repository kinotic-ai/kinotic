/**
 * A single server node in the cluster, of the org, system or app server. Mirrors the server's
 * {@code org.kinotic.system.api.model.cluster.KinoticNodeInfo}.
 */
export class KinoticNodeInfo {
    public nodeId: string = ''
    public order: number = 0
    /** The server kind the node runs, such as management, system or app. */
    public serverName: string = ''
    public addresses: string[] = []
    public hostNames: string[] = []
    /** The Kinotic version the node runs; null when the node runs from classes rather than a packaged jar. */
    public version: string | null = null
    /** The service name that labels the logs of the server the node runs, for LogService's server log methods; null when the deployment configures none. */
    public telemetryServiceName: string | null = null
    /** The service instance id that labels the node's own logs, narrowing a server log query to this node; null when the deployment configures none. */
    public telemetryServiceInstanceId: string | null = null
}
