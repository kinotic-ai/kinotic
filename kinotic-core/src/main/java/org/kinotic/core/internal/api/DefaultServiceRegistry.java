package org.kinotic.core.internal.api;

import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import org.kinotic.core.api.Kinotic;
import org.kinotic.core.api.RpcServiceProxies;
import org.kinotic.core.api.RpcServiceProxyFactory;
import org.kinotic.core.api.RpcServiceProxyHandle;
import org.kinotic.core.api.ServiceRegistry;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.TraceLogFilter;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.service.ServiceDescriptor;
import org.kinotic.core.api.service.FunctionInstanceProvider;
import org.kinotic.core.api.service.RequestLivenessWatcher;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.internal.api.service.ExceptionConverterComposite;
import org.kinotic.core.internal.api.service.invoker.ArgumentResolverComposite;
import org.kinotic.core.internal.api.service.invoker.ReturnValueConverterComposite;
import org.kinotic.core.internal.api.service.invoker.ServiceInvocationSupervisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

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
    @Autowired
    private RpcServiceProxyFactory proxyFactory;
    @Autowired
    private Vertx vertx; //TODO: move thread scheduling and execution functionality into the Kinotic API such as Scheduling Service ect..
    @Autowired
    private SecurityContext securityContext;
    @Autowired
    private OpenTelemetry openTelemetry;
    // resolved lazily per send failure: the directory bean is conditional and may not exist
    @Autowired
    private ObjectProvider<ServiceDirectory> serviceDirectoryProvider;
    @Autowired
    private TraceLogFilter traceLogFilter;
    @Autowired
    private RequestLivenessWatcher requestLivenessWatcher;
    // the proxies of this cluster member: their requests go through the event bus as the calling context's
    // participant, and an unreachable service is reported to the directory when there is one
    private RpcServiceProxies proxies;

    @PostConstruct
    void initProxies() {
        proxies = proxyFactory.over(eventBusService,
                                    requestLivenessWatcher,
                                    securityContext::currentParticipant,
                                    serviceDirectoryProvider::getIfAvailable,
                                    kinotic.serverInfo().getNodeName());
    }

    @Override
    public Future<Void> register(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface, Object instance) {
        try {
            return register(ServiceDescriptor.create(serviceIdentifier, serviceInterface), FunctionInstanceProvider.create(instance));
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<Void> register(ServiceDescriptor serviceDescriptor, FunctionInstanceProvider instanceProvider) {
        Promise<Void> promise = Promise.promise();
        supervisors.compute(serviceDescriptor.serviceIdentifier(),
                            (_, serviceInvocationSupervisor) -> {
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
                                                securityContext,
                                                openTelemetry,
                                                traceLogFilter);

                                        serviceInvocationSupervisor
                                                .start()
                                                .onComplete(ar -> {
                                                    if(ar.succeeded()){
                                                        promise.complete();
                                                    }else{
                                                        promise.fail(ar.cause());
                                                    }
                                                });

                                    } catch (Exception e) {
                                        promise.fail(e);
                                    }
                                }else{
                                    promise.fail(new IllegalArgumentException("Service already registered for ServiceIdentifier "+ serviceDescriptor.serviceIdentifier()));
                                }
                                return serviceInvocationSupervisor;
                            });
        return promise.future();
    }

    @Override
    public <T> RpcServiceProxyHandle<T> serviceProxy(ServiceIdentifier serviceIdentifier, Class<T> serviceInterface) {
        return proxies.serviceProxy(serviceIdentifier, serviceInterface);
    }

    @Override
    public <T> RpcServiceProxyHandle<T> serviceProxy(ServiceIdentifier serviceIdentifier, Class<T> serviceInterface, String contentTypeExpected) {
        return proxies.serviceProxy(serviceIdentifier, serviceInterface, contentTypeExpected);
    }

    @Override
    public <T> RpcServiceProxyHandle<T> serviceProxy(Class<T> serviceInterface) {
        return proxies.serviceProxy(serviceInterface);
    }

    @Override
    public Future<Void> unregister(ServiceIdentifier serviceIdentifier) {
        Promise<Void> promise = Promise.promise();
        supervisors.compute(serviceIdentifier,
                            (serviceIdentifier1, serviceInvocationSupervisor) -> {
                                if(serviceInvocationSupervisor != null){
                                    serviceInvocationSupervisor
                                            .stop()
                                            .onComplete(ar -> {
                                                if(ar.succeeded()){
                                                    promise.complete();
                                                }else{
                                                    promise.fail(ar.cause());
                                                }
                                            });
                                }else{
                                    promise.fail(new IllegalArgumentException(" No Service registered for for ServiceIdentifier "+ serviceIdentifier));
                                }
                                return null; // remove from map
                            });
        return promise.future();
    }
}
