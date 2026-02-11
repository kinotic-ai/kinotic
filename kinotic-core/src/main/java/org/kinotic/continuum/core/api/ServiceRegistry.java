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

package org.kinotic.continuum.core.api;

import org.kinotic.continuum.api.annotations.Proxy;
import org.kinotic.continuum.core.api.service.ServiceDescriptor;
import org.kinotic.continuum.core.api.service.ServiceFunctionInstanceProvider;
import org.kinotic.continuum.core.api.service.ServiceFunction;
import org.kinotic.continuum.core.api.service.ServiceIdentifier;
import reactor.core.publisher.Mono;

/**
 * Provides the functionality to register services as well as access proxies for those services
 *
 * Created by Navid Mitchell on 2019-02-08.
 */
public interface ServiceRegistry {

    /**
     * Registers a new service with the service registry.
     * This will allow the service to be accessed remotely.
     *
     * @param serviceIdentifier the identifier that is used to access this service remotely
     * @param serviceInterface the Interface that this service implements
     * @param instance the instance of this service that will respond to service invocations
     * @return {@link Mono} that will be completed on success or failed on an error
     *         NOTE: {@link Mono} will fail with an IllegalArgumentException if the service identifier is already registered with the registry
     */
    Mono<Void> register(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface, Object instance);

    /**
     * Registers a new service with the service registry.
     * This will allow the service to be accessed remotely.
     *
     * @param serviceDescriptor describes the service to be registered
     * @param instanceProvider to use when determining which object instance each {@link ServiceFunction} will be invoked on
     * @return {@link Mono} that will be completed on success or failed on an error
     *         NOTE: {@link Mono} will fail with an IllegalArgumentException if the service identifier is already registered with the registry
     */
    Mono<Void> register(ServiceDescriptor serviceDescriptor, ServiceFunctionInstanceProvider instanceProvider);

    /**
     * Remove a service from the service registry.
     *
     * @param serviceIdentifier the identifier that is used to identify the service
     * @return a {@link Mono} that will be completed when the service is unregistered
     */
    Mono<Void> unregister(ServiceIdentifier serviceIdentifier);

    /**
     * Creates a new service proxy that can be used to access the desired service.
     *
     * @param <T> the type of the serviceInterface
     * @param serviceIdentifier the identifier of the service to be accessed
     * @param serviceInterface the interface that the service should implement and the target class of the returned proxy
     * @return the {@link RpcServiceProxyHandle<T>} that can be used to access the service
     */
    <T> RpcServiceProxyHandle<T> serviceProxy(ServiceIdentifier serviceIdentifier, Class<T> serviceInterface);

    /**
     * Creates a new service proxy that can be used to access the desired service.
     *
     * @param <T> the type of the serviceInterface
     * @param serviceIdentifier the identifier of the service to be accessed
     * @param serviceInterface the interface that the service should implement and the target class of the returned proxy
     * @param contentTypeExpected a String containing the content type expected by the receiver of the RPC request
     * @return the {@link RpcServiceProxyHandle<T>} that can be used to access the service
     */
    <T> RpcServiceProxyHandle<T> serviceProxy(ServiceIdentifier serviceIdentifier, Class<T> serviceInterface, String contentTypeExpected);

    /**
     * Creates a new service proxy that can be used to access the desired service.
     * NOTE: the interface provided must have a {@link Proxy} annotation
     *
     * @param serviceInterface the interface that the service should implement and the target class of the returned proxy
     * @param <T> the type of the serviceInterface
     * @return the {@link RpcServiceProxyHandle<T>} that can be used to access the service
     * @throws IllegalArgumentException if the {@link Proxy} annotation is not present or the proxy value is not provided
     */
    <T> RpcServiceProxyHandle<T> serviceProxy(Class<T> serviceInterface);
}
