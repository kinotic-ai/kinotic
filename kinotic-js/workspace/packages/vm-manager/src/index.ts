import { Kinotic } from '@kinotic-ai/core'
import type { ConnectionInfo } from '@kinotic-ai/core'
import { VmNode, VmNodeStatus } from '@kinotic-ai/os-api'
import { VmManager } from '@/api/VmManager'
import os from 'node:os'

// Required configuration
const nodeId = process.env.KINOTIC_NODE_ID ?? Bun.argv[2]
if (!nodeId) {
    console.error('Error: KINOTIC_NODE_ID environment variable or command line argument is required')
    process.exit(1)
}

const serverHost = process.env.KINOTIC_SERVER_HOST ?? 'localhost'
const serverPort = Number(process.env.KINOTIC_SERVER_PORT ?? '58503')

async function start() {
    // Connect to the Kinotic server
    const connectionInfo: ConnectionInfo = {
        host: serverHost,
        port: serverPort,
    }
    await Kinotic.connect(connectionInfo)
    console.log(`Connected to Kinotic server at ${serverHost}:${serverPort}`)

    // Create and register the VmManager service (automatically registered via @Publish + @Scope)
    const vmManager = new VmManager(nodeId)

    // Build node info from system resources
    const node = new VmNode(nodeId, os.hostname(), os.hostname())
    node.status = VmNodeStatus.ONLINE
    node.totalCpus = os.cpus().length
    node.totalMemoryMb = Math.floor(os.totalmem() / (1024 * 1024))

    // Register this node with the NodeOrchestrationService on the server
    const nodeOrchestratorProxy = Kinotic.serviceProxy('org.kinotic.orchestrator.api.workload.NodeOrchestrationService')
    const registeredNode = await nodeOrchestratorProxy.invoke('registerNode', [node])

    console.log(`VM Manager registered on node: ${nodeId}`)
    console.log(`  CPUs: ${node.totalCpus}, Memory: ${node.totalMemoryMb}MB`)
}

start().catch(error => {
    console.error('Failed to start VM Manager:', error)
    process.exit(1)
})

export { VmManager } from '@/api/VmManager'
export type { IVmProvider } from '@/api/providers/IVmProvider'
export { BoxliteProvider } from '@/api/providers/BoxliteProvider'
