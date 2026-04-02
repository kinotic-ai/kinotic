import { VmManager } from '@/api/VmManager'

const vmManager = new VmManager()

console.log('Kinotic VM Manager starting...')

// The VmManager will be registered as a Kinotic service so it can receive
// workload requests via the Kinotic service proxy protocol.
// For now, export the manager instance for direct usage.
export { vmManager }
export { VmManager } from '@/api/VmManager'
export type { IVmProvider } from '@/api/providers/IVmProvider'
export { BoxliteProvider } from '@/api/providers/BoxliteProvider'
