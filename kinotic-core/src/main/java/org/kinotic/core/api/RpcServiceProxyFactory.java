package org.kinotic.core.api;

import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.event.RpcTransport;
import org.kinotic.core.api.event.TraceLogFilter;
import org.kinotic.core.api.service.RequestLivenessWatcher;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.core.internal.api.service.DefaultExceptionConverter;
import org.kinotic.core.internal.api.service.ExceptionConverter;
import org.kinotic.core.internal.api.service.rpc.DefaultResponseConverter;
import org.kinotic.core.internal.api.service.rpc.DefaultRpcReturnValueHandlerFactory;
import org.kinotic.core.internal.api.service.rpc.DefaultRpcServiceProxyHandle;
import org.kinotic.core.internal.api.service.rpc.RpcArgumentConverter;
import org.kinotic.core.internal.api.service.rpc.RpcProxyServices;
import org.kinotic.core.internal.api.service.rpc.RpcResponseConverter;
import org.kinotic.core.internal.api.service.rpc.RpcReturnValueHandlerFactory;
import org.kinotic.core.internal.api.service.rpc.converters.JacksonRpcArgumentConverter;
import org.kinotic.core.internal.config.KinoticJacksonConfig;
import org.springframework.core.ReactiveAdapterRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mints the service proxies a {@link ServiceRegistry} hands out, for a process that has no Spring context. It
 * assembles once what every proxy shares: the Jackson conversion of arguments and replies, the handling of every
 * supported return type ({@code CompletableFuture}, Vert.x {@code Future}, {@code Mono} and {@code Flux}), the
 * mapping of a service's exception back to the caller and the tracing of each invocation. Every proxy it mints
 * sends through the given {@link RpcTransport} and watches its pending requests with the given
 * {@link RequestLivenessWatcher}, so a request whose receiver is gone fails instead of waiting forever. The
 * invocations name no participant: the gateway a request passes through sets the sender from the connection.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
public class RpcServiceProxyFactory {

    // the adapters depend on the classpath alone, so every factory of the process shares one registry
    private static final ReactiveAdapterRegistry REACTIVE_ADAPTERS = new ReactiveAdapterRegistry();

    static {
        KinoticUtil.registerVertxFutureAdapter(REACTIVE_ADAPTERS);
    }

    private final RpcArgumentConverter argumentConverter;
    private final RpcProxyServices services;
    /**
     * @param transport              carries the proxies' requests and replies
     * @param requestLivenessWatcher fails a proxy's request when the receiver its acknowledgement named is gone
     * @param vertx                  the Vert.x instance the proxies run on
     * @param openTelemetry          traces the proxies' invocations
     * @param properties             the {@link KinoticProperties#getMaxEventPayloadSize()} bounding the replies
     *                               the proxies parse
     * @param jsonMapper             converts the proxies' arguments and replies; one built from
     *                               {@link #jsonMapperBuilder()} converts as a cluster member does
     * @param nodeName               names this process in the reply destinations of its proxies
     */
    public RpcServiceProxyFactory(RpcTransport transport,
                                  RequestLivenessWatcher requestLivenessWatcher,
                                  Vertx vertx,
                                  OpenTelemetry openTelemetry,
                                  KinoticProperties properties,
                                  JsonMapper jsonMapper,
                                  String nodeName) {
        Validate.notNull(properties, "properties must not be null");
        Validate.notNull(jsonMapper, "jsonMapper must not be null");
        Validate.notBlank(nodeName, "nodeName must not be blank");

        this.argumentConverter = new JacksonRpcArgumentConverter(jsonMapper, REACTIVE_ADAPTERS, properties);
        // the same assembly a cluster member's Spring context makes, so the supported set has one home
        ExceptionConverter exceptionConverter = new DefaultExceptionConverter(properties, jsonMapper);
        RpcResponseConverter responseConverter = new DefaultResponseConverter(jsonMapper, REACTIVE_ADAPTERS, properties);
        RpcReturnValueHandlerFactory returnValueHandlerFactory = new DefaultRpcReturnValueHandlerFactory(responseConverter, exceptionConverter);
        this.services = new RpcProxyServices(nodeName,
                                             returnValueHandlerFactory,
                                             transport,
                                             // a process outside the cluster authenticates no participant of its
                                             // own; the gateway names the connection's participant as the sender
                                             () -> null,
                                             // and holds no service directory to report an unreachable service to
                                             () -> null,
                                             requestLivenessWatcher,
                                             vertx,
                                             openTelemetry,
                                             new TraceLogFilter(properties));
    }

    /**
     * A builder of the Jackson mapper the platform converts service arguments and replies with, as
     * {@code KinoticJacksonConfig} configures a cluster member's: the Vert.x types in their Vert.x wire format,
     * the platform's own value types, and no constructor parameter detection. A process adds the modules its
     * services' types need before building the mapper it gives the factory.
     * @return the builder
     */
    public static JsonMapper.Builder jsonMapperBuilder() {
        return KinoticJacksonConfig.configure(JsonMapper.builder());
    }

    /**
     * Creates a proxy for the service the interface addresses.
     * @param serviceInterface the interface of the service, annotated with {@link org.kinotic.core.api.annotations.Proxy}
     *                         or {@link org.kinotic.core.api.annotations.Publish}
     * @param <T> the type of the serviceInterface
     * @return the {@link RpcServiceProxyHandle} that gives access to the proxy and releases it
     */
    public <T> RpcServiceProxyHandle<T> serviceProxy(Class<T> serviceInterface) {
        return serviceProxy(KinoticUtil.serviceIdentifierOf(serviceInterface), serviceInterface);
    }

    /**
     * Creates a proxy for the service identified, implementing the interface.
     * @param serviceIdentifier the identifier of the service to be accessed
     * @param serviceInterface  the interface that the service implements and the target class of the returned proxy
     * @param <T> the type of the serviceInterface
     * @return the {@link RpcServiceProxyHandle} that gives access to the proxy and releases it
     */
    public <T> RpcServiceProxyHandle<T> serviceProxy(ServiceIdentifier serviceIdentifier, Class<T> serviceInterface) {
        return new DefaultRpcServiceProxyHandle<>(serviceIdentifier,
                                                  serviceInterface,
                                                  argumentConverter,
                                                  serviceInterface.getClassLoader(),
                                                  services);
    }

}
