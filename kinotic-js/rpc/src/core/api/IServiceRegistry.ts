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

import {ContextInterceptor, ServiceContext} from '@/core/api/ContextInterceptor.js'
import {ServiceIdentifier} from '@/core/api/ServiceIdentifier.js'
import { Observable } from 'rxjs'
import { IEvent } from './IEventBus'

/**
 * Provides an interface to allow the {@link IServiceProxy} creator the ability to have fine grain control of the outgoing {@link IEvent}
 */
export interface IEventFactory {

    create(cri: string, args: any[] | null | undefined): IEvent

}

/**
 * {@link IServiceProxy} provides the ability to access a remote service
 *
 * Created by navid on 2019-04-18.
 */
export interface IServiceProxy {

    /**
     * The remote id of the service that this proxy is for
     */
    serviceIdentifier: string

    /**
     * Provides functionality to invoke the remote service that returns a single result
     * @param methodIdentifier of the service method to invoke
     * @param args to pass to the service invocation
     * @param scope string or undefined or null, if not undefined or null this is used to determine which service instance rpc requests should be routed to.
     * @param eventFactory IEventFactory or undefined or null, if not undefined or null this is the {@link IEventFactory} to use when creating {@link IEvent}'s to send
     * @return a {@link Promise} that will resolve to the result from the invocation
     */
    invoke(methodIdentifier: string, args?: any[] | null | undefined, scope?: string | null | undefined, eventFactory?: IEventFactory | null | undefined): Promise<any>

    /**
     * Provides functionality to invoke the remote service that returns a stream of results
     * @param methodIdentifier of the service method to invoke
     * @param args to pass to the service invocation
     * @param scope string or undefined or null, if not undefined or null this is used to determine which service instance rpc requests should be routed to.
     * @param eventFactory IEventFactory or undefined or null, if not undefined or null this is the {@link IEventFactory} to use when creating {@link IEvent}'s to send
     * @return a {@link Observable} that will resolve to the result from the invocation
     */
    invokeStream(methodIdentifier: string, args?: any[] | null | undefined, scope?: string | null | undefined, eventFactory?: IEventFactory | null | undefined): Observable<any>

}


/**
 * Provides the functionality to register services as well as create proxies for those services
 *
 * Created by Navid Mitchell on 2019-02-08.
 */
export interface IServiceRegistry {

    /**
     * Creates a new service proxy that can be used to access the desired service.
     * @param serviceIdentifier the identifier of the service to be accessed
     * @return the {@link IServiceProxy} that can be used to access the service
     */
    serviceProxy(serviceIdentifier: string): IServiceProxy


    /**
     * Registers a new service with the service registry.
     * This will allow the service to be accessed remotely.
     *
     * @param serviceIdentifier identifies the service to be registered
     * @param service to use invoke when the service is called
     */
    register(serviceIdentifier: ServiceIdentifier, service: any): void

    /**
     * Unregisters a service with the service registry.
     * This will remove the service from the registry, and it will no longer be accessible remotely.
     *
     * @param serviceIdentifier identifies the service to be unregistered
     */
    unRegister(serviceIdentifier: ServiceIdentifier): void

    /**
     * Registers a {@link ContextInterceptor} that will be used to modify the {@link ServiceContext} before the service method is invoked.
     * This allows for custom context data to be added or modified before the service method is called.
     *
     * @param interceptor the {@link ContextInterceptor} to register
     */
    registerContextInterceptor<T extends ServiceContext>(interceptor: ContextInterceptor<T> | null): void

}
