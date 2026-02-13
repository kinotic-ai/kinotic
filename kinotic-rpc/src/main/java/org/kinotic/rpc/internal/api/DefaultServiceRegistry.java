

package org.kinotic.rpc.internal.api;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.Validate;
import org.kinotic.boot.api.Kinotic;
import org.kinotic.rpc.api.annotations.Proxy;
import org.kinotic.rpc.api.RpcServiceProxyHandle;
import org.kinotic.rpc.api.ServiceRegistry;
import org.kinotic.rpc.api.event.EventBusService;
import org.kinotic.rpc.api.service.ServiceDescriptor;
import org.kinotic.rpc.api.service.ServiceFunctionInstanceProvider;
import org.kinotic.rpc.api.service.ServiceIdentifier;
import org.kinotic.rpc.internal.api.service.invoker.ArgumentResolverComposite;
import org.kinotic.rpc.internal.api.service.ExceptionConverterComposite;
import org.kinotic.rpc.internal.api.service.invoker.ReturnValueConverterComposite;
import org.kinotic.rpc.internal.api.service.invoker.ServiceInvocationSupervisor;
import org.kinotic.rpc.internal.api.service.rpc.DefaultRpcServiceProxyHandle;
import org.kinotic.rpc.internal.api.service.rpc.RpcArgumentConverter;
import org.kinotic.rpc.internal.api.service.rpc.RpcArgumentConverterResolver;
import org.kinotic.rpc.internal.api.service.rpc.RpcReturnValueHandlerFactory;
import org.kinotic.boot.internal.utils.KinoticUtil;
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
    private Kinotic kinotic;
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
                                                  kinotic.serverInfo().getNodeName(),
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
                                                  kinotic.serverInfo().getNodeName(),
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

        String namespace = proxyAnnotation.namespace().isEmpty() ? KinoticUtil.safeEncodeURI(serviceInterface.getPackageName()) : KinoticUtil.safeEncodeURI(proxyAnnotation.namespace());
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
