import type { PortProtocol } from '@/api/model/workload/PortProtocol'

/**
 * A guest port exposed on the host for a workload's VM.
 */
export interface PortMapping {

    /** Host port to expose. When omitted, the provider assigns one. */
    hostPort?: number

    /** Guest port to map to the host. */
    guestPort: number

    /** Transport protocol. When omitted, the provider's default applies. */
    protocol?: PortProtocol

    /** Host interface to bind on. When omitted, the provider's default applies. */
    hostIp?: string
}
