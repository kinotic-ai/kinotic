import { VmManager } from '@/api/VmManager'

// The node id should be provided as an environment variable or command line argument
const nodeId = process.env.KINOTIC_NODE_ID ?? Bun.argv[2]
if (!nodeId) {
    console.error('Error: KINOTIC_NODE_ID environment variable or command line argument is required')
    process.exit(1)
}

const vmManager = new VmManager(nodeId)

console.log(`Kinotic VM Manager starting on node: ${nodeId}`)

export { vmManager }
export { VmManager } from '@/api/VmManager'
export type { IVmProvider } from '@/api/providers/IVmProvider'
export { BoxliteProvider } from '@/api/providers/BoxliteProvider'
