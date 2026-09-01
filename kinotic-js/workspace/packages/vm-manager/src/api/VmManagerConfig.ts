import os from 'node:os'
import path from 'node:path'
import { VmProviderType } from '@kinotic-ai/system-api'

/**
 * Resolves KINOTIC_VM_PROVIDER, defaulting to the provider that runs anywhere a developer
 * works. An unrecognised name throws rather than falling back, so a node configured for a
 * provider it cannot spell never silently registers running something else.
 */
function parseProviderType(value: string | undefined): VmProviderType {
    let ret: VmProviderType
    if (!value) {
        ret = VmProviderType.BOXLITE
    } else if ((Object.values(VmProviderType) as string[]).includes(value)) {
        ret = value as VmProviderType
    } else {
        throw new Error(`KINOTIC_VM_PROVIDER must be one of `
                        + `${Object.values(VmProviderType).join(', ')} but was '${value}'`)
    }
    return ret
}

/**
 * Resolves KINOTIC_WORKLOAD_DATA_DIR, defaulting to a directory the node can create the
 * mounts of a workload under with the authority it runs with. A CLOUD_HYPERVISOR node is a
 * provisioned machine whose vm-manager runs as a service account under /var/lib, while a
 * BOXLITE node is whatever machine a developer started one on, where that path is root-owned.
 */
function defaultWorkloadDataDir(providerType: VmProviderType): string {
    return providerType === VmProviderType.BOXLITE
        ? path.join(os.homedir(), '.kinotic', 'workloads')
        : '/var/lib/kinotic/workloads'
}

/**
 * Typed view of the vm-manager's process configuration. Every environment variable the
 * vm-manager reads is resolved here, so all env var usage is traceable from this class.
 * Server and credential settings are not among them: Kinotic.connect() resolves those
 * itself from the standard KINOTIC_SERVER_ and KINOTIC_CLIENT_ (or KINOTIC_TOKEN) variables.
 */
export class VmManagerConfig {

    /** KINOTIC_VM_PROVIDER — the provider every workload on this node runs on. */
    readonly providerType: VmProviderType = parseProviderType(process.env.KINOTIC_VM_PROVIDER)

    /** KINOTIC_NODE_ID — unique id of this vm-manager node. */
    readonly nodeId: string | undefined = process.env.KINOTIC_NODE_ID

    /** KINOTIC_HEARTBEAT_INTERVAL_MS — period of the node heartbeat sent to the orchestrator. */
    readonly heartbeatIntervalMs: number = Number(process.env.KINOTIC_HEARTBEAT_INTERVAL_MS ?? '30000')

    /**
     * KINOTIC_WORKLOAD_DNS — resolver given to each workload and permitted on port 53. A
     * property of the node's network rather than of any workload, which is why it is not
     * carried on NetworkPolicy: a workload cannot know what resolver its node was given.
     */
    readonly workloadDns: string | undefined = process.env.KINOTIC_WORKLOAD_DNS

    /** KINOTIC_LOKI_URL — Loki HTTP API workload logs are shipped to; unset disables log shipping. */
    readonly lokiUrl: string | undefined = process.env.KINOTIC_LOKI_URL

    /**
     * KINOTIC_WORKLOAD_DATA_DIR — base directory every workload volume mount on this node
     * must live under. Mounts are bound with the vm-manager's authority, so this boundary is
     * what keeps a workload spec from mounting an arbitrary host directory. Reported to the
     * server at registration so deployment flows compose host paths under it. Defaults to
     * /var/lib/kinotic/workloads, and to ~/.kinotic/workloads on a BOXLITE node.
     */
    readonly workloadDataDir: string = process.env.KINOTIC_WORKLOAD_DATA_DIR
                                       ?? defaultWorkloadDataDir(this.providerType)

    /** KINOTIC_VM_LOGS_DIR — base directory holding each workload's log dir mounted into its guest. */
    readonly vmLogsDir: string = process.env.KINOTIC_VM_LOGS_DIR ?? path.join(os.homedir(), '.kinotic', 'vm-logs')

    /** BOXLITE_HOME — boxlite's store for box records and guest rootfs disks. */
    readonly boxliteHome: string = process.env.BOXLITE_HOME ?? path.join(os.homedir(), '.boxlite')

    /** Workload state persisted for recovery across vm-manager restarts. Fixed location, not per-environment. */
    readonly vmStateDir: string = path.join(os.homedir(), '.kinotic', 'vm-state')

    /** Alloy's generated config, WAL, and downloaded binaries. Fixed location, not per-environment. */
    readonly alloyDataDir: string = path.join(os.homedir(), '.kinotic', 'alloy')
}
