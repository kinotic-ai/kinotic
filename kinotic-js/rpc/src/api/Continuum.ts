/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { EventBus } from '@/core/api/EventBus'
import {IEventBus} from '@/core/api/IEventBus'
import { ServiceRegistry } from '@/core/api/ServiceRegistry'
import {IEventFactory, IServiceProxy} from '@/core/api/IServiceRegistry'
import {Identifiable} from '@/api/Identifiable'
import {ICrudServiceProxy} from '@/core/api/crud/ICrudServiceProxy'
import {CrudServiceProxyFactory} from '@/core/api/crud/CrudServiceProxyFactory'
import {ConnectedInfo} from '@/api/security/ConnectedInfo'
import {ConnectionInfo} from '@/api/ConnectionInfo'


/**
 * Provides a way to store the current {@link ContinuumSingleton} and {@link IEventFactory} instances
 * This allows the {@link IServiceProxy} instances to dynamically switch between different {@link ContinuumSingleton} and {@link IEventFactory} instances
 */
type ContinuumContext = {
    instance?: ContinuumSingleton
    eventFactory?: IEventFactory
}

/**
 * Interface that defines the methods needed to manage the current {@link ContinuumContext} instance
 */
interface IContinuumContextStack {
    /**
     * Returns the current Continuum instance to use for all {@link IServiceProxy} instances invoked with {@link ContinuumContext.execute}
     */
    getContinuumInstance(): ContinuumSingleton | undefined

    /**
     * Returns the current event factory to use for all {@link IServiceProxy} instances invoked with {@link ContinuumContext.execute}
     */
    getEventFactory(): IEventFactory | undefined
}

/**
 * Provides a way to store the current {@link ContinuumContext} instance
 * This is used to allow for {@link IServiceProxy} instances to dynamically switch between different {@link ContinuumSingleton} instances
 */
class ContinuumContextStackClass implements IContinuumContextStack {

    private continuumOverride: ContinuumContext[] = []

    getContinuumInstance(): ContinuumSingleton | undefined {
        return this.continuumOverride[this.continuumOverride.length - 1]?.instance
    }

    getEventFactory(): IEventFactory | undefined{
        return this.continuumOverride[this.continuumOverride.length - 1]?.eventFactory
    }

    /**
     * Sets the current {@link ContinuumContext} instance to use for all {@link IServiceProxy} instances invoked with {@link ContinuumContext.execute}
     * @param continuumContext
     */
    push(continuumContext: ContinuumContext): void {
        this.continuumOverride.push(continuumContext)
    }

    /**
     * Removes the current {@link ContinuumContext} from the stack
     */
    pop(): void {
        this.continuumOverride.pop()
    }
}

/**
 * The default {@link ContinuumContextStackClass} instance that can be used to manage the current {@link ContinuumContext} instance
 */
export const ContinuumContextStack: IContinuumContextStack = new ContinuumContextStackClass()

/**
 * Provides a simplified way to connect to Continuum and access services.
 * All methods use a single connection to the Continuum Services
 */
export class ContinuumSingleton {
    /**
     * The {@link IEventBus} that is used to communicate with the Continuum server
     */
    private _eventBus!: IEventBus
    /**
     * The {@link ServiceRegistry} that is used to manage the services that are available
     */
    public readonly serviceRegistry!: ServiceRegistry
    /**
     * The {@link CrudServiceProxyFactory} that is used to create {@link ICrudServiceProxy} instances
     */
    public readonly crudServiceProxyFactory!: CrudServiceProxyFactory

    constructor() {
        this._eventBus = new EventBus()
        this.serviceRegistry = new ServiceRegistry(this._eventBus)
        this.crudServiceProxyFactory = new CrudServiceProxyFactory(this.serviceRegistry)
    }

    public get eventBus(): IEventBus {
        return this._eventBus
    }

    public set eventBus(eventBus: IEventBus) {
        this._eventBus = eventBus
        this.serviceRegistry.eventBus = eventBus
    }

    /**
     * Requests a connection to the given Stomp url
     * @param connectionInfo provides the information needed to connect to the continuum server
     * @return Promise containing the result of the initial connection attempt
     */
     public connect(connectionInfo: ConnectionInfo): Promise<ConnectedInfo> {
        return this._eventBus.connect(connectionInfo)
    }

    /**
     * Disconnects the client from the server
     * This will clear any subscriptions and close the connection
     */
    public disconnect(force?: boolean): Promise<void> {
        return this._eventBus.disconnect(force)
    }

    /**
     * Creates a new service proxy that can be used to access the desired service.
     * @param serviceIdentifier the identifier of the service to be accessed
     * @return the {@link IServiceProxy} that can be used to access the service
     */
    public serviceProxy(serviceIdentifier: string): IServiceProxy {
        return this.serviceRegistry.serviceProxy(serviceIdentifier)
    }

    /**
     * Returns a {@link ICrudServiceProxy} for the given service identifier
     * @param serviceIdentifier the identifier of the service to be accessed
     */
    public crudServiceProxy<T extends Identifiable<string>>(serviceIdentifier: string): ICrudServiceProxy<T> {
        return this.crudServiceProxyFactory.crudServiceProxy<T>(serviceIdentifier)
    }

    /**
     * Allows for the execution of a function that requires a connection to the Continuum server
     * When the function is executed any calls using an {@link IServiceProxy} will be executed using the connection defined by this {@link ContinuumSingleton}
     * @param toExecute the function to execute
     * @param eventFactory an optional {@link IEventFactory} to use for the duration of the execution
     * @return the result of the function that was executed
     * @deprecated we have decided to deprecate this method as it is not thread safe and can cause issues when used in async operations
     * NOTE: this method is experimental and does not work well across multiple threads / async operations
     */
    public async execute(toExecute: () => Promise<any>, eventFactory?: IEventFactory): Promise<any> {
        const stack = ContinuumContextStack as ContinuumContextStackClass
        stack.push({instance: this, eventFactory: eventFactory})

        let ret: any
        try {
            ret = await toExecute()
        } finally {
            stack.pop()
        }

        return ret
    }
}

/**
 * The default {@link ContinuumSingleton} instance that can be used to access Continuum services
 */
export const Continuum = new ContinuumSingleton()
