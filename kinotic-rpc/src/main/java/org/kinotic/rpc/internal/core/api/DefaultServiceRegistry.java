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

package org.kinotic.rpc.internal.core.api;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.Validate;
import org.kinotic.rpc.api.Continuum;
import org.kinotic.rpc.api.annotations.Proxy;
import org.kinotic.rpc.api.RpcServiceProxyHandle;
import org.kinotic.rpc.api.ServiceRegistry;
import org.kinotic.rpc.api.event.EventBusService;
import org.kinotic.rpc.api.service.ServiceDescriptor;
import org.kinotic.rpc.api.service.ServiceFunctionInstanceProvider;
import org.kinotic.rpc.api.service.ServiceIdentifier;
import org.kinotic.rpc.internal.core.api.service.invoker.ArgumentResolverComposite;
import org.kinotic.rpc.internal.core.api.service.ExceptionConverterComposite;
import org.kinotic.rpc.internal.core.api.service.invoker.ReturnValueConverterComposite;
import org.kinotic.rpc.internal.core.api.service.invoker.ServiceInvocationSupervisor;
import org.kinotic.rpc.internal.core.api.service.rpc.DefaultRpcServiceProxyHandle;
import org.kinotic.rpc.internal.core.api.service.rpc.RpcArgumentConverter;
import org.kinotic.rpc.internal.core.api.service.rpc.RpcArgumentConverterResolver;
import org.kinotic.rpc.internal.core.api.service.rpc.RpcReturnValueHandlerFactory;
import org.kinotic.rpc.internal.utils.ContinuumUtil;
import org.kinotic.rpc.internal.utils.MetaUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Vertx;
import reactor.core.publisher.Mono;

/**
 *
 * Created by Navid Mitchell on 2019-03-20.
 */
@Component
public class DefaultServiceRegistry implements ServiceRegistry {

    private final ConcurrentHashMap<ServiceIdentifier, ServiceInvocationSupervisor> supervisors = new ConcurrentHashMap<>();
    // These converters are used by ServiceInvocationSupervisor
    @Autowired
    private ArgumentResolverComposite argumentResolver;
    @Autowired
    private Continuum continuum;
    @Autowired
    private EventBusService eventBusService;
    @Autowired
    private ExceptionConverterComposite exceptionConverter;
    @Autowired
    private ReactiveAdapterRegistry reactiveAdapterRegistry;
    @Autowired
    private ReturnValueConverterComposite returnValueConverter;
    // These are used for proxy side logic
    @Autowired
    private RpcArgumentConverterResolver rpcArgumentConverterResolver;
    @Autowired
    private RpcReturnValueHandlerFactory rpcReturnValueHandlerFactory;
    @Autowired
    private Vertx vertx; //TODO: move thread scheduling and execution functionality into Continuum API such as Scheduling Service ect..
    @Autowired
    private OpenTelemetry openTelemetry;

    @Override
    public Mono<Void> register(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface, Object instance) {
        try {
            return register(ServiceDescriptor.create(serviceIdentifier, serviceInterface), ServiceFunctionInstanceProvider.create(instance));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Void> register(ServiceDescriptor serviceDescriptor, ServiceFunctionInstanceProvider instanceProvider) {
        return Mono.create(sink -> supervisors.compute(serviceDescriptor.serviceIdentifier(),
                                                       (serviceIdentifier, serviceInvocationSupervisor) -> {
                                                           if(serviceInvocationSupervisor == null){
                                                               try {
                                                                   serviceInvocationSupervisor = new ServiceInvocationSupervisor(
                                                                           serviceDescriptor,
                                                                           instanceProvider,
                                                                           argumentResolver,
                                                                           returnValueConverter,
                                                                           exceptionConverter,
                                                                           eventBusService,
                                                                           reactiveAdapterRegistry,
                                                                           vertx,
                                                                           openTelemetry);

                                                                   serviceInvocationSupervisor
                                                                           .start()
                                                                           .subscribe(sink::success,
                                                                                      sink::error);

                                                               } catch (Exception e) {
                                                                   sink.error(e);
                                                               }
                                                           }else{
                                                               sink.error(new IllegalArgumentException("Service already registered for ServiceIdentifier "+ serviceDescriptor.serviceIdentifier()));
                                                           }
                                                           return serviceInvocationSupervisor;
                                                       }));
    }

    @Override
    public <T> RpcServiceProxyHandle<T> serviceProxy(ServiceIdentifier serviceIdentifier, Class<T> serviceInterface) {
        RpcArgumentConverter rpcArgumentConverter = rpcArgumentConverterResolver.resolve(MimeTypeUtils.APPLICATION_JSON_VALUE);
        return new DefaultRpcServiceProxyHandle<>(serviceIdentifier,
                                                  continuum.serverInfo().getNodeName(),
                                                  serviceInterface,
                                                  rpcArgumentConverter,
                                                  rpcReturnValueHandlerFactory,
                                                  eventBusService,
                                                  Thread.currentThread().getContextClassLoader());
    }

    @Override
    public <T> RpcServiceProxyHandle<T> serviceProxy(ServiceIdentifier serviceIdentifier, Class<T> serviceInterface, String contentTypeExpected) {
        Validate.notBlank(contentTypeExpected, "The contentTypeExpected must not be blank");
        Validate.isTrue(rpcArgumentConverterResolver.canResolve(contentTypeExpected), "The contentType:"+contentTypeExpected+" does not have any configured RpcArgumentConverter's");
        RpcArgumentConverter rpcArgumentConverter = rpcArgumentConverterResolver.resolve(contentTypeExpected);
        return new DefaultRpcServiceProxyHandle<>(serviceIdentifier,
                                                  continuum.serverInfo().getNodeName(),
                                                  serviceInterface,
                                                  rpcArgumentConverter,
                                                  rpcReturnValueHandlerFactory,
                                                  eventBusService,
                                                  Thread.currentThread().getContextClassLoader());
    }

    @Override
    public <T> RpcServiceProxyHandle<T> serviceProxy(Class<T> serviceInterface) {
        Proxy proxyAnnotation = serviceInterface.getAnnotation(Proxy.class);
        Validate.notNull(proxyAnnotation, "The Class provided must be annotated with @Proxy");

        String namespace = proxyAnnotation.namespace().isEmpty() ? ContinuumUtil.safeEncodeURI(serviceInterface.getPackageName()) : ContinuumUtil.safeEncodeURI(proxyAnnotation.namespace());
        String name = proxyAnnotation.name().isEmpty() ? serviceInterface.getSimpleName() : proxyAnnotation.name();
        String version = MetaUtil.getVersion(serviceInterface);

        ServiceIdentifier serviceIdentifier = new ServiceIdentifier(namespace,
                                                                    name,
                                                                    null,
                                                                    version);

        return serviceProxy(serviceIdentifier, serviceInterface, MimeTypeUtils.APPLICATION_JSON_VALUE);
    }

    @Override
    public Mono<Void> unregister(ServiceIdentifier serviceIdentifier) {
        return Mono.create(sink -> supervisors.compute(serviceIdentifier,
                                                       (serviceIdentifier1, serviceInvocationSupervisor) -> {
                                                           if(serviceInvocationSupervisor != null){
                                                               serviceInvocationSupervisor
                                                                       .stop()
                                                                       .subscribe(sink::success,
                                                                                  sink::error);
                                                           }else{
                                                               sink.error(new IllegalArgumentException(" No Service registered for for ServiceIdentifier "+ serviceIdentifier));
                                                           }
                                                           return null; // remove from map
                                                       }));
    }
}
