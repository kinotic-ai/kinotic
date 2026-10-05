import type {ServiceDefinition} from '@kinotic-ai/idl'

/**
 * A service of an application as its runtime registers it in the platform's service directory: the application
 * it belongs to, where it is addressed, whether it is advertised, and its definition with the decorators it
 * declares. The platform owns everything else about the directory's entry: the checks it derives, the tools the
 * functions declare, and the service's liveness.
 */
export interface ServiceDirectoryEntry {
    /**
     * The application the service belongs to, which its zone names.
     */
    applicationId: string

    /**
     * The zone the service is addressed in, the application's or a label under it.
     */
    zone: string

    /**
     * The version the service registers under, or null for none.
     */
    version: string | null

    /**
     * True when the service advertised itself with `@Publish(advertise = true)` and appears in directory listings.
     */
    advertised: boolean

    /**
     * The service's definition: its functions, and the decorators it declares.
     */
    serviceDefinition: ServiceDefinition
}
