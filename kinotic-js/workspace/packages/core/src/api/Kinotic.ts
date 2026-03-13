import {ConnectionInfo} from '@/api/ConnectionInfo'
import type {Identifiable} from '@/api/Identifiable'
import {ConnectedInfo} from '@/api/security/ConnectedInfo'
import {CrudServiceProxyFactory} from '@/core/api/crud/CrudServiceProxyFactory'
import type {ICrudServiceProxy} from '@/core/api/crud/ICrudServiceProxy'
import {EventBus} from '@/core/api/EventBus'
import type {IEventBus} from '@/core/api/IEventBus'
import type {IServiceProxy, IServiceRegistry} from '@/core/api/IServiceRegistry'
import {ServiceRegistry} from '@/core/api/ServiceRegistry'


interface IKinotic {
    serviceRegistry: IServiceRegistry
    eventBus: IEventBus

    /**
     * Requests a connection to the given Stomp url
     * @param connectionInfo provides the information needed to connect to the kinoitc server
     * @return Promise containing the result of the initial connection attempt
     */
    connect(connectionInfo: ConnectionInfo): Promise<ConnectedInfo>

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
     * Requests a connection to the given Stomp url
     * @param connectionInfo provides the information needed to connect to the kinoitc server
     * @return Promise containing the result of the initial connection attempt
     */
    connect(connectionInfo: ConnectionInfo): Promise<ConnectedInfo> {
        return this._eventBus.connect(connectionInfo)
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

}

/**
 * The default {@link IKinotic} instance that can be used to access Kinotic services
 */
export const Kinotic: IKinotic = new KinoticSingleton()
