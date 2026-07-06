import { Kinotic, SessionKeepAliveMode, createAuthenticatedWebSocketFactory } from '@kinotic-ai/core'
import type { ConnectionInfo, ServerInfo } from '@kinotic-ai/core'
import { VmNodeRegistration } from '@/model/VmNodeRegistration'
import { VmNodeOrchestrationServiceProxy } from '@/internal/services/VmNodeOrchestrationServiceProxy'
import { DefaultVmManager } from '@/internal/api/DefaultVmManager'
import { createAuthProviderFromEnv } from '@/api/auth/createAuthProviderFromEnv'
import { AlloyManager } from '@/internal/api/logging/AlloyManager'
import os from 'node:os'
import path from 'node:path'

// Required configuration
const nodeId = process.env.KINOTIC_NODE_ID ?? Bun.argv[2]
if (!nodeId) {
    console.error('Error: KINOTIC_NODE_ID environment variable or command line argument is required')
    process.exit(1)
}

const serverHost = process.env.KINOTIC_SERVER_HOST ?? 'localhost'
const serverPort = Number(process.env.KINOTIC_SERVER_PORT ?? '58503')
const serverUseSSL = (process.env.KINOTIC_SERVER_USE_SSL ?? 'false').toLowerCase() === 'true'
const heartbeatIntervalMs = Number(process.env.KINOTIC_HEARTBEAT_INTERVAL_MS ?? '30000')
const vmLogsDir = process.env.KINOTIC_VM_LOGS_DIR ?? path.join(os.homedir(), '.kinotic', 'vm-logs')
const lokiUrl = process.env.KINOTIC_LOKI_URL

const alloyManager = lokiUrl
    ? new AlloyManager({
        lokiUrl,
        nodeId: nodeId!,
        dataDir: path.join(os.homedir(), '.kinotic', 'alloy'),
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
    }, heartbeatIntervalMs)
}

async function start() {
    // Connect to the Kinotic server. As of @kinotic-ai/core 1.7.0 authentication
    // is performed during the WebSocket upgrade via a pluggable auth provider.
    const serverInfo: ServerInfo = {
        host: serverHost,
        port: serverPort,
        useSSL: serverUseSSL
    }
    const connectionInfo: ConnectionInfo = {
        ...serverInfo,
        sessionKeepAlive: SessionKeepAliveMode.ACTIVITY,
        webSocketFactory: createAuthenticatedWebSocketFactory(serverInfo, createAuthProviderFromEnv())
    }
    await Kinotic.connect(connectionInfo)
    console.log(`Connected to Kinotic server at ${serverHost}:${serverPort}`)

    // Create and register the VmManager service (automatically registered via @Publish + @Scope)
    const vmManager = new DefaultVmManager(nodeId!, vmLogsDir, alloyManager)

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
    console.log(`Heartbeat started (every ${heartbeatIntervalMs / 1000}s)`)
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
export { createAuthProviderFromEnv } from '@/api/auth/createAuthProviderFromEnv'
