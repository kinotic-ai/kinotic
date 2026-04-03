import { Kinotic } from '@kinotic-ai/core'
import type { ConnectionInfo } from '@kinotic-ai/core'
import { VmNodeRegistration } from '@/model/VmNodeRegistration'
import { VmNodeOrchestrationServiceProxy } from '@/internal/services/VmNodeOrchestrationServiceProxy'
import { DefaultVmManager } from '@/internal/api/DefaultVmManager'
import os from 'node:os'

// Required configuration
const nodeId = process.env.KINOTIC_NODE_ID ?? Bun.argv[2]
if (!nodeId) {
    console.error('Error: KINOTIC_NODE_ID environment variable or command line argument is required')
    process.exit(1)
}

const serverHost = process.env.KINOTIC_SERVER_HOST ?? 'localhost'
const serverPort = Number(process.env.KINOTIC_SERVER_PORT ?? '58503')
const serverLogin = process.env.KINOTIC_SERVER_LOGIN ?? 'kinotic'
const serverPasscode = process.env.KINOTIC_SERVER_PASSCODE ?? 'kinotic'
const heartbeatIntervalMs = Number(process.env.KINOTIC_HEARTBEAT_INTERVAL_MS ?? '30000')

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
    // Connect to the Kinotic server
    const connectionInfo: ConnectionInfo = {
        host: serverHost,
        port: serverPort,
        connectHeaders: { login: serverLogin, passcode: serverPasscode },
        disableStickySession: true
    }
    await Kinotic.connect(connectionInfo)
    console.log(`Connected to Kinotic server at ${serverHost}:${serverPort}`)

    // Create and register the VmManager service (automatically registered via @Publish + @Scope)
    const vmManager = new DefaultVmManager(nodeId!)

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
process.on('SIGINT', async () => {
    console.log('Shutting down VM Manager...')
    if (heartbeatTimer) {
        clearInterval(heartbeatTimer)
    }
    await Kinotic.disconnect()
    process.exit(0)
})

process.on('SIGTERM', async () => {
    console.log('Shutting down VM Manager...')
    if (heartbeatTimer) {
        clearInterval(heartbeatTimer)
    }
    await Kinotic.disconnect()
    process.exit(0)
})

start().catch(error => {
    console.error('Failed to start VM Manager:', error)
    process.exit(1)
})

export type { IVmManager } from '@/api/IVmManager'
export type { IVmProvider } from '@/internal/api/providers/IVmProvider'
