import os from 'node:os'
import path from 'node:path'

/**
 * Typed view of the vm-manager's process configuration. Every environment variable the
 * vm-manager reads is resolved here, so all env var usage is traceable from this class.
 * Server and credential settings are not among them: Kinotic.connect() resolves those
 * itself from the standard KINOTIC_SERVER_ and KINOTIC_CLIENT_ (or KINOTIC_TOKEN) variables.
 */
export class VmManagerConfig {

    /** KINOTIC_NODE_ID — unique id of this vm-manager node. */
    readonly nodeId: string | undefined = process.env.KINOTIC_NODE_ID

    /** KINOTIC_HEARTBEAT_INTERVAL_MS — period of the node heartbeat sent to the orchestrator. */
    readonly heartbeatIntervalMs: number = Number(process.env.KINOTIC_HEARTBEAT_INTERVAL_MS ?? '30000')

    /** KINOTIC_LOKI_URL — Loki HTTP API workload logs are shipped to; unset disables log shipping. */
    readonly lokiUrl: string | undefined = process.env.KINOTIC_LOKI_URL

    /** KINOTIC_VM_LOGS_DIR — base directory holding each workload's log dir mounted into its guest. */
    readonly vmLogsDir: string = process.env.KINOTIC_VM_LOGS_DIR ?? path.join(os.homedir(), '.kinotic', 'vm-logs')

    /** BOXLITE_HOME — boxlite's store for box records and guest rootfs disks. */
    readonly boxliteHome: string = process.env.BOXLITE_HOME ?? path.join(os.homedir(), '.boxlite')

    /** Workload state persisted for recovery across vm-manager restarts. Fixed location, not per-environment. */
    readonly vmStateDir: string = path.join(os.homedir(), '.kinotic', 'vm-state')

    /** Alloy's generated config, WAL, and downloaded binaries. Fixed location, not per-environment. */
    readonly alloyDataDir: string = path.join(os.homedir(), '.kinotic', 'alloy')
}
