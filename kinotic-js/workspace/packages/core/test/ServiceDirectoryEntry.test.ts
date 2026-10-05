import {FunctionDefinition, ServiceDefinition} from '@kinotic-ai/idl'
import {Observable, of, Subject} from 'rxjs'
import {afterEach, describe, expect, it} from 'vitest'
import {
    AuthzResource,
    declareServiceDefinitions,
    Event,
    EventConstants,
    type IEvent,
    type IEventBus,
    Kinotic,
    Publish,
    ServiceIdentifier,
    ServiceRegistry
} from '../src'

const ZONE = 'app.acme.crm'
const NAMESPACE = 'com.acme.reports'
const DIRECTORY_REGISTER = 'srv://app-api~org.kinotic.app.api.services.ServiceDirectoryService/register'

/**
 * The bus a registry registers its services' entries through: it records what the registry sends and answers
 * every request with an empty reply.
 */
class RecordingEventBus {
    public connected = false
    public readonly sent: IEvent[] = []
    public readonly connectionEnded = new Subject<Error | null>()

    isConnected(): boolean {
        return this.connected
    }

    observe(): Observable<IEvent> {
        return new Observable<IEvent>()
    }

    requestStream(event: IEvent): Observable<IEvent> {
        this.sent.push(event)
        const reply = new Event(event.cri)
        reply.setHeader(EventConstants.CONTENT_TYPE_HEADER, EventConstants.CONTENT_JSON)
        reply.setDataString('null')
        return of(reply)
    }

    /** The entries registered in the directory through this bus, as sent. */
    registrations(): any[] {
        return this.sent.filter(event => event.cri === DIRECTORY_REGISTER)
                        .map(event => JSON.parse(event.getDataString())[0])
    }

    asEventBus(): IEventBus {
        return this as unknown as IEventBus
    }
}

class ReportService {
    findReports(): Promise<string[]> {
        return Promise.resolve([])
    }

    generate(name: string): Promise<string> {
        return Promise.resolve(name)
    }
}

function definition(name: string, ...functions: string[]): ServiceDefinition {
    const ret = new ServiceDefinition(name, NAMESPACE)
    for (const fn of functions) {
        ret.addFunction(new FunctionDefinition(fn))
    }
    return ret
}

function identifier(name: string, zone: string | undefined = ZONE): ServiceIdentifier {
    const ret = new ServiceIdentifier(NAMESPACE, name, zone)
    ret.version = '1.2.0'
    return ret
}

function registry(connected: boolean): {registry: ServiceRegistry, bus: RecordingEventBus} {
    const bus = new RecordingEventBus()
    bus.connected = connected
    return {registry: new ServiceRegistry(bus.asEventBus()), bus}
}

/**
 * Pins what a runtime registers in the platform's directory: an application service with the definition the
 * project declared for it, as it registers on a connected client or as the client connects; nothing for a
 * service outside an application's zone or one with no definition; and a refusal for a definition naming other
 * functions than the service serves, or a resource service with no definition.
 */
describe('Kinotic JS', () => {
    describe('packages/core', () => {
        describe('Directory registration', () => {

            afterEach(() => {
                Kinotic.zonePrefix = null
            })

            it('registers an application service with its declared definition as it registers on a connected client', () => {
                declareServiceDefinitions([definition('ReportService', 'findReports', 'generate')])
                const {registry: services, bus} = registry(true)

                services.register(identifier('ReportService'), new ReportService())

                const registrations = bus.registrations()
                expect(registrations).toHaveLength(1)
                expect(registrations[0].applicationId).toBe('crm')
                expect(registrations[0].zone).toBe(ZONE)
                expect(registrations[0].version).toBe('1.2.0')
                expect(registrations[0].advertised).toBe(false)
                expect(registrations[0].serviceDefinition.namespace).toBe(NAMESPACE)
                expect(registrations[0].serviceDefinition.name).toBe('ReportService')
                expect(registrations[0].serviceDefinition.functions.map((f: any) => f.name)).toEqual(['findReports', 'generate'])
            })

            it('registers the services registered before the connection when the client connects', async () => {
                declareServiceDefinitions([definition('ReportService', 'findReports', 'generate')])
                const {registry: services, bus} = registry(false)

                services.register(identifier('ReportService'), new ReportService())
                expect(bus.registrations()).toHaveLength(0)

                bus.connected = true
                await services.registerEntries()
                expect(bus.registrations()).toHaveLength(1)
            })

            it('refuses a service whose definition declares other functions than it serves', () => {
                declareServiceDefinitions([definition('DriftedService', 'findReports')])
                const {registry: services, bus} = registry(true)

                expect(() => services.register(identifier('DriftedService'), new ReportService())).toThrowError(/kinotic sync/)
                expect(bus.registrations()).toHaveLength(0)
            })

            it('refuses a resource service the project declared no definition for', () => {
                @AuthzResource('invoice')
                class InvoiceService {
                    findInvoices(): Promise<string[]> {
                        return Promise.resolve([])
                    }
                }
                const {registry: services} = registry(true)

                expect(() => services.register(identifier('InvoiceService'), new InvoiceService())).toThrowError(/declares a resource/)
            })

            it('serves a service with no definition on its zone alone', () => {
                const {registry: services, bus} = registry(true)

                services.register(identifier('UndeclaredService'), new ReportService())

                expect(bus.registrations()).toHaveLength(0)
            })

            it('registers nothing for a service outside an application zone', () => {
                declareServiceDefinitions([definition('PlatformService', 'findReports', 'generate')])
                const {registry: services, bus} = registry(true)

                services.register(identifier('PlatformService', 'system'), new ReportService())

                expect(bus.registrations()).toHaveLength(0)
            })

            it('registers a published service as advertised when Publish says so', () => {
                declareServiceDefinitions([definition('AdvertisedService', 'ping')])
                const bus = new RecordingEventBus()
                bus.connected = true
                Kinotic.eventBus = bus.asEventBus()
                Kinotic.zonePrefix = ZONE

                @Publish(NAMESPACE, undefined, true)
                class AdvertisedService {
                    ping(): string {
                        return 'pong'
                    }
                }
                new AdvertisedService()

                const registrations = bus.registrations()
                expect(registrations).toHaveLength(1)
                expect(registrations[0].advertised).toBe(true)
                expect(registrations[0].zone).toBe(ZONE)
            })
        })
    })
})
