import os from 'node:os'
import path from 'node:path'

/**
 * Typed view of the vm-manager's process configuration. Every environment variable the
 * vm-manager reads is resolved here, so all env var usage is traceable from this class.
 */
export class VmManagerConfig {

    /** KINOTIC_NODE_ID — unique id of this vm-manager node. */
    readonly nodeId: string | undefined = process.env.KINOTIC_NODE_ID

    /** KINOTIC_SERVER_HOST — Kinotic server hostname. */
    readonly serverHost: string = process.env.KINOTIC_SERVER_HOST ?? 'localhost'

    /** KINOTIC_SERVER_PORT — Kinotic server WebSocket port. */
    readonly serverPort: number = Number(process.env.KINOTIC_SERVER_PORT ?? '58503')

    /** KINOTIC_SERVER_USE_SSL — 'true' to connect to the server with TLS. */
    readonly serverUseSSL: boolean = (process.env.KINOTIC_SERVER_USE_SSL ?? 'false').toLowerCase() === 'true'

    /** KINOTIC_AUTH_TYPE — server authentication mechanism: 'basic' (default) or 'bearer'. */
    readonly authType: string = (process.env.KINOTIC_AUTH_TYPE ?? 'basic').toLowerCase()

    /** KINOTIC_SERVER_LOGIN — login used when authType is 'basic'. */
    readonly serverLogin: string = process.env.KINOTIC_SERVER_LOGIN ?? 'kinotic'

    /** KINOTIC_SERVER_PASSCODE — passcode used when authType is 'basic'. */
    readonly serverPasscode: string = process.env.KINOTIC_SERVER_PASSCODE ?? 'kinotic'

    /** KINOTIC_SERVER_TOKEN — token used when authType is 'bearer'; required in that mode. */
    readonly serverToken: string | undefined = process.env.KINOTIC_SERVER_TOKEN

    /** KINOTIC_HEARTBEAT_INTERVAL_MS — period of the node heartbeat sent to the orchestrator. */
    readonly heartbeatIntervalMs: number = Number(process.env.KINOTIC_HEARTBEAT_INTERVAL_MS ?? '30000')

    /** KINOTIC_LOKI_URL — Loki HTTP API workload logs are shipped to; unset disables log shipping. */
    readonly lokiUrl: string | undefined = process.env.KINOTIC_LOKI_URL

    /** KINOTIC_VM_LOGS_DIR — base directory holding each workload's log dir mounted into its guest. */
    readonly vmLogsDir: string = process.env.KINOTIC_VM_LOGS_DIR ?? path.join(os.homedir(), '.kinotic', 'vm-logs')

    /** Workload state persisted for recovery across vm-manager restarts. Fixed location, not per-environment. */
    readonly vmStateDir: string = path.join(os.homedir(), '.kinotic', 'vm-state')

    /** Alloy's generated config, WAL, and downloaded binaries. Fixed location, not per-environment. */
    readonly alloyDataDir: string = path.join(os.homedir(), '.kinotic', 'alloy')
}
