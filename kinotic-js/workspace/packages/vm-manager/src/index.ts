import { Kinotic } from '@kinotic-ai/core'
import { ensureNodeWebSocket } from '@kinotic-ai/core/node'
import { VmNodeRegistration } from '@kinotic-ai/system-api'
import { VmNodeOrchestrationServiceProxy } from '@kinotic-ai/system-api'
import { DefaultVmManager } from '@/internal/api/DefaultVmManager'
import { BoxliteProvider } from '@/internal/api/providers/BoxliteProvider'
import { CloudHypervisorProvider } from '@/internal/api/providers/CloudHypervisorProvider'
import { EgressPolicyManager } from '@/internal/api/network/EgressPolicyManager'
import type { IVmProvider } from '@/internal/api/providers/IVmProvider'
import { VmManagerConfig } from '@/api/VmManagerConfig'
import { AlloyManager } from '@/internal/api/logging/AlloyManager'
import { SYSTEM_API_ZONE, VmProviderType } from '@kinotic-ai/system-api'
import type { Workload } from '@kinotic-ai/system-api'
import type { WorkloadStatusReport } from '@kinotic-ai/system-api'
import Docker from 'dockerode'
import os from 'node:os'
import { join } from 'node:path'

const config = new VmManagerConfig()

const nodeId = config.nodeId ?? Bun.argv[2]
if (!nodeId) {
    console.error('Error: KINOTIC_NODE_ID environment variable or command line argument is required')
    process.exit(1)
}

const alloyManager = config.lokiUrl
    ? new AlloyManager({
        lokiUrl: config.lokiUrl,
        nodeId,
        dataDir: config.alloyDataDir,
    })
    : null
if (!alloyManager) {
    console.warn('KINOTIC_LOKI_URL is not set — workload log shipping is disabled')
}

let heartbeatTimer: Timer | null = null

// The node runs whichever provider it is configured for and nothing else, so a provider it
// cannot construct is a fatal misconfiguration rather than a capability to omit
function createProvider(reportStatus: (workload: Workload) => void): IVmProvider {
    let ret: IVmProvider
    if (config.providerType === VmProviderType.BOXLITE) {
        ret = new BoxliteProvider(config.boxliteHome, config.vmLogsDir, config.vmStateDir, reportStatus)
    } else if (config.providerType === VmProviderType.CLOUD_HYPERVISOR) {
        const egress = new EgressPolicyManager(config.workloadDns ?? null)
        if (!egress.enforces()) {
            console.warn('This node does not deny workload egress by default — a workload can reach '
                         + 'anything its address can route to. See docker-kata-ch/README.md')
        }
        ret = new CloudHypervisorProvider(join(config.vmStateDir, 'cloud-hypervisor'),
                                          new Docker(),
                                          config.workloadDataDir,
                                          egress,
                                          config.workloadDns ?? null,
                                          reportStatus)
    } else {
        throw new Error(`No provider implementation for ${config.providerType}`)
    }
    return ret
}

function toStatusReport(workload: Workload): WorkloadStatusReport {
    return {
        workloadId: workload.id!,
        status: workload.status,
        exitCode: workload.exitCode ?? null,
        updated: workload.updated ?? Date.now(),
    }
}

/**
 * Why this node cannot ship the logs of the workloads it runs, if it cannot. A workload whose
 * output goes nowhere is not one this node should be given, so this joins the invariants the
 * provider checks — the node keeps what it is running and stops being offered more.
 *
 * Only a node that was asked to ship logs can fail to: one started without KINOTIC_LOKI_URL
 * has already said so at startup.
 */
function logShippingProblems(): string[] {
    const problem = alloyManager?.shippingProblem() ?? null
    return problem !== null ? [problem] : []
}

function startHeartbeat(nodeOrchestrator: VmNodeOrchestrationServiceProxy,
                        vmManager: DefaultVmManager,
                        provider: IVmProvider) {
    heartbeatTimer = setInterval(async () => {
        try {
            // A node that stopped enforcing something keeps its workloads but takes no more
            await nodeOrchestrator.heartbeat(nodeId!, [...await provider.checkNodeHealth(),
                                                      ...logShippingProblems()])
            // Snapshot reconciliation: re-reporting everything converges any transition
            // whose push was lost while the server was unreachable
            const workloads = await vmManager.listWorkloads()
            if (workloads.length > 0) {
                await nodeOrchestrator.reportWorkloadStatus(nodeId!, workloads.map(toStatusReport))
            }
        } catch (error) {
            console.error('Heartbeat failed:', error)
        }
    }, config.heartbeatIntervalMs)
}

async function start() {
    // The vm-manager runs as a system participant, so its VmManager service registers in the
    // system zone. Must be set before DefaultVmManager is instantiated (@Publish registers there).
    Kinotic.zonePrefix = SYSTEM_API_ZONE

    // Server and credentials resolve from the environment: KINOTIC_SERVER_HOST/PORT/USE_SSL
    // and KINOTIC_CLIENT_ID/KINOTIC_CLIENT_SECRET (or KINOTIC_TOKEN).
    ensureNodeWebSocket()
    await Kinotic.connect()
    const server = Kinotic.eventBus.serverInfo
    console.log(`Connected to Kinotic server at ${server?.host}:${server?.port}`)

    const nodeOrchestrator = new VmNodeOrchestrationServiceProxy(Kinotic)

    // Reattach to workloads a previous vm-manager process left running before the
    // VmManager service is published and can receive new workload operations. Every
    // node-side status transition is pushed so the server tracks the workload's real
    // state; a failed push is only logged — the heartbeat snapshot reconciles it.
    const reportStatus = (workload: Workload) => {
        nodeOrchestrator.reportWorkloadStatus(nodeId!, [toStatusReport(workload)])
                        .catch(error => console.error('Failed to report workload status:', error))
    }
    const provider = createProvider(reportStatus)
    await provider.recover()

    // Create and register the VmManager service (automatically registered via @Publish + @Scope)
    const vmManager = new DefaultVmManager(nodeId!, provider, alloyManager)

    // Resume shipping the recovered workloads' logs, which also downloads and launches
    // Alloy here rather than inside whichever startWorkload call arrives first
    await vmManager.refreshLogShipping()

    // Build registration info from system resources
    const registration = new VmNodeRegistration(nodeId!, os.hostname(), os.hostname())
    registration.providerType = provider.type
    registration.totalCpus = os.cpus().length
    registration.totalMemoryMb = Math.floor(os.totalmem() / (1024 * 1024))
    registration.totalDiskMb = await provider.totalDiskMb()
    registration.workloadDataDir = config.workloadDataDir

    // Register this node with the VmNodeOrchestrationService on the server
    await nodeOrchestrator.registerNode(registration)

    console.log(`VM Manager registered on node: ${nodeId} (provider ${provider.type})`)
    console.log(`  CPUs: ${registration.totalCpus}, Memory: ${registration.totalMemoryMb}MB, `
                + `Disk: ${registration.totalDiskMb}MB`)

    // Start sending periodic heartbeats
    startHeartbeat(nodeOrchestrator, vmManager, provider)
    console.log(`Heartbeat started (every ${config.heartbeatIntervalMs / 1000}s)`)
}

// Graceful shutdown
async function shutdown() {
    console.log('Shutting down VM Manager...')
    if (heartbeatTimer) {
        clearInterval(heartbeatTimer)
    }
    await alloyManager?.stop()
    await Kinotic.disconnect()
    process.exit(0)
}

process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)

start().catch(error => {
    console.error('Failed to start VM Manager:', error)
    process.exit(1)
})

export type { IVmManager } from '@/api/IVmManager'
export type { IVmProvider } from '@/internal/api/providers/IVmProvider'
export type { VolumeMount } from '@kinotic-ai/system-api'
export { VmManagerConfig } from '@/api/VmManagerConfig'
