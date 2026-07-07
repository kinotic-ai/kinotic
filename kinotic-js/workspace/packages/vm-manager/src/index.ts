import { Kinotic, SessionKeepAliveMode, createAuthenticatedWebSocketFactory } from '@kinotic-ai/core'
import type { ConnectionInfo, ServerInfo } from '@kinotic-ai/core'
import { VmNodeRegistration } from '@/model/VmNodeRegistration'
import { VmNodeOrchestrationServiceProxy } from '@/internal/services/VmNodeOrchestrationServiceProxy'
import { DefaultVmManager } from '@/internal/api/DefaultVmManager'
import { BoxliteProvider } from '@/internal/api/providers/BoxliteProvider'
import { VmManagerConfig } from '@/api/VmManagerConfig'
import { createAuthProvider } from '@/api/createAuthProvider'
import { AlloyManager } from '@/internal/api/logging/AlloyManager'
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

function startHeartbeat(nodeOrchestrator: VmNodeOrchestrationServiceProxy) {
    heartbeatTimer = setInterval(async () => {
        try {
            await nodeOrchestrator.heartbeat(nodeId!)
        } catch (error) {
            console.error('Heartbeat failed:', error)
        }
    }, config.heartbeatIntervalMs)
}

async function start() {
    // Connect to the Kinotic server. As of @kinotic-ai/core 1.7.0 authentication
    // is performed during the WebSocket upgrade via a pluggable auth provider.
    const serverInfo: ServerInfo = {
        host: config.serverHost,
        port: config.serverPort,
        useSSL: config.serverUseSSL
    }
    const connectionInfo: ConnectionInfo = {
        ...serverInfo,
        sessionKeepAlive: SessionKeepAliveMode.ACTIVITY,
        webSocketFactory: createAuthenticatedWebSocketFactory(serverInfo, createAuthProvider(config))
    }
    await Kinotic.connect(connectionInfo)
    console.log(`Connected to Kinotic server at ${config.serverHost}:${config.serverPort}`)

    // Reattach to workloads a previous vm-manager process left running before the
    // VmManager service is published and can receive new workload operations
    const provider = new BoxliteProvider(config.vmLogsDir, config.vmStateDir)
    await provider.recover()

    // Create and register the VmManager service (automatically registered via @Publish + @Scope)
    const vmManager = new DefaultVmManager(nodeId!, provider, alloyManager)

    // Build registration info from system resources
    const registration = new VmNodeRegistration(nodeId!, os.hostname(), os.hostname())
    registration.totalCpus = os.cpus().length
    registration.totalMemoryMb = Math.floor(os.totalmem() / (1024 * 1024))

    // Register this node with the VmNodeOrchestrationService on the server
    const nodeOrchestrator = new VmNodeOrchestrationServiceProxy(
        Kinotic.serviceProxy('org.kinotic.orchestrator.api.workload.VmNodeOrchestrationService')
    )
    await nodeOrchestrator.registerNode(registration)

    console.log(`VM Manager registered on node: ${nodeId}`)
    console.log(`  CPUs: ${registration.totalCpus}, Memory: ${registration.totalMemoryMb}MB`)

    // Start sending periodic heartbeats
    startHeartbeat(nodeOrchestrator)
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
export { createAuthProvider } from '@/api/createAuthProvider'
