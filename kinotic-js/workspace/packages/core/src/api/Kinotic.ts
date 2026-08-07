import type {ConnectOptions} from '@/api/ConnectOptions'
import {resolveConnectOptions} from '@/internal/api/ConnectResolution'
import {ConnectedInfo} from '@/api/security/ConnectedInfo'
import {EventBus} from '@/api/event/EventBus'
import type {IEventBus} from '@/api/event/IEventBus'
import type {IServiceProxy, IServiceRegistry} from '@/api/IServiceRegistry'
import {ServiceRegistry} from '@/api/ServiceRegistry'


/**
 * A plugin that can be installed into a {@link IKinotic} instance to extend it with additional services.
 */
export interface KinoticPlugin<TExtension extends object> {
    install(kinotic: IKinotic): TExtension
}

export interface IKinotic {
    serviceRegistry: IServiceRegistry
    eventBus: IEventBus

    /**
     * The zone prefix every service published by this client registers under, such as
     * `app.<organizationId>.<applicationId>` for an application or `system` for a platform
     * workload. Must be set from static configuration before service classes are instantiated.
     * When null, services register at un-zoned legacy addresses.
     */
    zonePrefix: string | null

    /**
     * The zone applied to published services that carry no {@link Zone} declaration of their
     * own, typically loaded from the project package.json `kinotic.zone` field.
     */
    defaultZone: string | null

    /**
     * Connects to a Kinotic server. Every option is optional: absent server fields and
     * credentials resolve from the environment — see {@link ConnectOptions}.
     * @param options overrides for the resolved connection, or nothing to resolve everything
     * @return Promise containing the result of the initial connection attempt
     */
    connect(options?: ConnectOptions): Promise<ConnectedInfo>

    /**
     * Disconnects the client from the server
     * This will clear any subscriptions and close the connection
     */
    disconnect(force?: boolean): Promise<void>

    /**
     * Creates a new service proxy that can be used to access the desired service.
     * @param serviceIdentifier the identifier of the service to be accessed
     * @return the {@link IServiceProxy} that can be used to access the service
     */
    serviceProxy(serviceIdentifier: string): IServiceProxy

    /**
     * Installs a plugin into this Kinotic instance, extending it with additional typed properties.
     * @param plugin the plugin to install
     * @return this instance extended with the plugin's properties
     */
    use<TExtension extends object>(plugin: KinoticPlugin<TExtension>): this & TExtension

}


/**
 * Provides a simplified way to connect to Kinotic and access services.
 * All methods use a single connection to the Kinotic Services
 */
export class KinoticSingleton implements IKinotic {
    /**
     * The {@link IEventBus} that is used to communicate with the Kinotic server
     */
    private _eventBus!: IEventBus

    /**
     * The {@link ServiceRegistry} that is used to manage the services that are available
     */
    readonly serviceRegistry!: ServiceRegistry

    public zonePrefix: string | null = null

    public defaultZone: string | null = null

    constructor() {
        this._eventBus = new EventBus()
        this.serviceRegistry = new ServiceRegistry(this._eventBus)
    }

    get eventBus(): IEventBus {
        return this._eventBus
    }

    public set eventBus(eventBus: IEventBus) {
        this._eventBus = eventBus
        this.serviceRegistry.eventBus = eventBus
    }

    /**
     * Connects to a Kinotic server. Every option is optional: absent server fields and
     * credentials resolve from the environment — see {@link ConnectOptions}.
     * @param options overrides for the resolved connection, or nothing to resolve everything
     * @return Promise containing the result of the initial connection attempt
     */
    connect(options?: ConnectOptions): Promise<ConnectedInfo> {
        return this._eventBus.connect(resolveConnectOptions(options))
    }

    /**
     * Disconnects the client from the server
     * This will clear any subscriptions and close the connection
     */
    disconnect(force?: boolean): Promise<void> {
        return this._eventBus.disconnect(force)
    }

    /**
     * Creates a new service proxy that can be used to access the desired service.
     * @param serviceIdentifier the identifier of the service to be accessed
     * @return the {@link IServiceProxy} that can be used to access the service
     */
    serviceProxy(serviceIdentifier: string): IServiceProxy {
        return this.serviceRegistry.serviceProxy(serviceIdentifier)
    }

    /**
     * Installs a plugin into this Kinotic instance, extending it with additional typed properties.
     * @param plugin the plugin to install
     * @return this instance extended with the plugin's properties
     */
    use<TExtension extends object>(plugin: KinoticPlugin<TExtension>): this & TExtension {
        const extension = plugin.install(this)
        return Object.assign(this, extension) as this & TExtension
    }

}

/**
 * The default {@link IKinotic} instance that can be used to access Kinotic services
 */
export const Kinotic: KinoticSingleton = new KinoticSingleton()
