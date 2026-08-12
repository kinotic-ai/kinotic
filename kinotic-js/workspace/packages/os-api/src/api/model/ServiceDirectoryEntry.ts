import type { ServiceDefinition } from '@kinotic-ai/idl'
import type { Identifiable } from '@kinotic-ai/core'

/**
 * A registered service contract and its verified liveness. Mirrors the server's
 * {@code org.kinotic.core.api.directory.ServiceDirectoryEntry}.
 */
export class ServiceDirectoryEntry implements Identifiable<string> {
    public id: string | null = null
    public serviceAddress: string = ''
    public organizationId: string | null = null
    public applicationId: string | null = null
    public projectId: string | null = null
    public namespace: string | null = null
    public name: string = ''
    public version: string = ''
    public zone: string | null = null
    public description: string | null = null
    public serviceDefinition: ServiceDefinition | null = null
    public advertised: boolean = false
    public mcpExposed: boolean = false
    public online: boolean = false
    /** ISO-8601 instant of the last liveness transition. */
    public lastStatusChange: string | null = null
}
