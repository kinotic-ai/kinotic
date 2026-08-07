import { Kinotic } from '@kinotic-ai/core'
import { ensureNodeWebSocket } from '@kinotic-ai/core/node'
import { VmNodeRegistration } from '@/model/VmNodeRegistration'
import { VmNodeOrchestrationServiceProxy } from '@/internal/services/VmNodeOrchestrationServiceProxy'
import { DefaultVmManager } from '@/internal/api/DefaultVmManager'
import { BoxliteProvider } from '@/internal/api/providers/BoxliteProvider'
import { VmManagerConfig } from '@/api/VmManagerConfig'
import { AlloyManager } from '@/internal/api/logging/AlloyManager'
import { SYSTEM_ZONE } from '@kinotic-ai/os-api'
import type { Workload } from '@kinotic-ai/os-api'
import type { WorkloadStatusReport } from '@/model/WorkloadStatusReport'
import os from 'node:os'

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

function toStatusReport(workload: Workload): WorkloadStatusReport {
    return {
        workloadId: workload.id!,
        status: workload.status,
        updated: workload.updated ?? Date.now(),
    }
}

function startHeartbeat(nodeOrchestrator: VmNodeOrchestrationServiceProxy, vmManager: DefaultVmManager) {
    heartbeatTimer = setInterval(async () => {
        try {
            await nodeOrchestrator.heartbeat(nodeId!)
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
    Kinotic.zonePrefix = SYSTEM_ZONE

    // Server and credentials resolve from the environment: KINOTIC_SERVER_HOST/PORT/USE_SSL
    // and KINOTIC_CLIENT_ID/KINOTIC_CLIENT_SECRET (or KINOTIC_TOKEN).
    ensureNodeWebSocket()
    await Kinotic.connect()
    const server = Kinotic.eventBus.serverInfo
    console.log(`Connected to Kinotic server at ${server?.host}:${server?.port}`)

    const nodeOrchestrator = new VmNodeOrchestrationServiceProxy(
        Kinotic.serviceProxy(`${SYSTEM_ZONE}~org.kinotic.orchestrator.api.workload.VmNodeOrchestrationService`)
    )

    // Reattach to workloads a previous vm-manager process left running before the
    // VmManager service is published and can receive new workload operations. Every
    // node-side status transition is pushed so the server tracks the workload's real
    // state; a failed push is only logged — the heartbeat snapshot reconciles it.
    const provider = new BoxliteProvider(config.vmLogsDir, config.vmStateDir, workload => {
        nodeOrchestrator.reportWorkloadStatus(nodeId!, [toStatusReport(workload)])
                        .catch(error => console.error('Failed to report workload status:', error))
    })
    await provider.recover()

    // Create and register the VmManager service (automatically registered via @Publish + @Scope)
    const vmManager = new DefaultVmManager(nodeId!, provider, alloyManager)

    // Build registration info from system resources
    const registration = new VmNodeRegistration(nodeId!, os.hostname(), os.hostname())
    registration.totalCpus = os.cpus().length
    registration.totalMemoryMb = Math.floor(os.totalmem() / (1024 * 1024))

    // Register this node with the VmNodeOrchestrationService on the server
    await nodeOrchestrator.registerNode(registration)

    console.log(`VM Manager registered on node: ${nodeId}`)
    console.log(`  CPUs: ${registration.totalCpus}, Memory: ${registration.totalMemoryMb}MB`)

    // Start sending periodic heartbeats
    startHeartbeat(nodeOrchestrator, vmManager)
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
export type { VolumeMount } from '@kinotic-ai/os-api'
export { VmManagerConfig } from '@/api/VmManagerConfig'
