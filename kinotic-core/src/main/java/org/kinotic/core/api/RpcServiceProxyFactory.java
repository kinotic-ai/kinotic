package org.kinotic.core.api;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.RpcTransport;
import org.kinotic.core.api.event.TraceLogFilter;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.service.RequestLivenessWatcher;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.core.internal.api.service.ExceptionConverter;
import org.kinotic.core.internal.api.service.json.JacksonExceptionConverter;
import org.kinotic.core.internal.api.service.rpc.DefaultRpcServiceProxyHandle;
import org.kinotic.core.internal.api.service.rpc.RpcArgumentConverter;
import org.kinotic.core.internal.api.service.rpc.RpcResponseConverter;
import org.kinotic.core.internal.api.service.rpc.RpcResponseConverterComposite;
import org.kinotic.core.internal.api.service.rpc.RpcReturnValueHandlerFactory;
import org.kinotic.core.internal.api.service.rpc.RpcReturnValueHandlerFactoryComposite;
import org.kinotic.core.internal.api.service.rpc.converters.BinaryRpcResponseConverter;
import org.kinotic.core.internal.api.service.rpc.converters.JacksonRpcArgumentConverter;
import org.kinotic.core.internal.api.service.rpc.converters.JacksonRpcResponseConverter;
import org.kinotic.core.internal.api.service.rpc.converters.TextRpcResponseConverter;
import org.kinotic.core.internal.api.service.rpc.types.CompletableFutureValueHandlerFactory;
import org.kinotic.core.internal.api.service.rpc.types.FluxReturnValueHandlerFactory;
import org.kinotic.core.internal.api.service.rpc.types.MonoRpcReturnValueHandlerFactory;
import org.kinotic.core.internal.api.service.rpc.types.VertxFutureRpcReturnValueHandlerFactory;
import org.kinotic.core.internal.config.KinoticJacksonConfig;
import org.kinotic.vertx.jackson3.VertxJackson3Module;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ReactiveAdapterRegistry;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mints the service proxies a {@link ServiceRegistry} hands out, for a process that has no Spring context. It
 * assembles once what every proxy shares: the Jackson conversion of arguments and replies, the handling of every
 * supported return type ({@code CompletableFuture}, Vert.x {@code Future}, {@code Mono} and {@code Flux}), the
 * mapping of a service's exception back to the caller and the tracing of each invocation. Every proxy it mints
 * sends through the given {@link RpcTransport} and watches its pending requests with the given
 * {@link RequestLivenessWatcher}, so a request whose receiver is gone fails instead of waiting forever.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
public class RpcServiceProxyFactory {

    private final RpcTransport transport;
    private final RequestLivenessWatcher requestLivenessWatcher;
    private final Vertx vertx;
    private final SecurityContext securityContext;
    private final String nodeName;
    private final JsonMapper jsonMapper;
    private final RpcArgumentConverter argumentConverter;
    private final RpcReturnValueHandlerFactory returnValueHandlerFactory;
    private final TraceLogFilter traceLogFilter;
    private final OpenTelemetry openTelemetry;
    // a process outside the cluster holds no service directory to report an unreachable service to
    private final ObjectProvider<ServiceDirectory> serviceDirectoryProvider = new ObjectProvider<>() {
        @Override
        public ServiceDirectory getIfAvailable() {
            return null;
        }
    };

    /**
     * @param transport              carries the proxies' requests and replies
     * @param requestLivenessWatcher fails a proxy's request when the receiver its acknowledgement named is gone
     * @param vertx                  the Vert.x instance the proxies run on, created after {@code securityContext}:
     *                               a context sizes its locals from the context-locals registered when its Vert.x
     *                               instance was created, and the {@link SecurityContext} registers the participant
     *                               local when it is loaded
     * @param securityContext        supplies the participant an invocation is sent as
     * @param properties             the {@link KinoticProperties#getMaxEventPayloadSize()} bounding the replies
     *                               the proxies parse
     * @param jsonMapper             converts the proxies' arguments and replies; one built from
     *                               {@link #jsonMapperBuilder()} converts as a cluster member does
     * @param nodeName               names this process in the reply destinations of its proxies
     */
    public RpcServiceProxyFactory(RpcTransport transport,
                                  RequestLivenessWatcher requestLivenessWatcher,
                                  Vertx vertx,
                                  SecurityContext securityContext,
                                  KinoticProperties properties,
                                  JsonMapper jsonMapper,
                                  String nodeName) {
        Validate.notNull(transport, "transport must not be null");
        Validate.notNull(requestLivenessWatcher, "requestLivenessWatcher must not be null");
        Validate.notNull(vertx, "vertx must not be null");
        Validate.notNull(securityContext, "securityContext must not be null");
        Validate.notNull(properties, "properties must not be null");
        Validate.notNull(jsonMapper, "jsonMapper must not be null");
        Validate.notBlank(nodeName, "nodeName must not be blank");
        this.transport = transport;
        this.requestLivenessWatcher = requestLivenessWatcher;
        this.vertx = vertx;
        this.securityContext = securityContext;
        this.nodeName = nodeName;
        this.jsonMapper = jsonMapper;
        this.traceLogFilter = new TraceLogFilter(properties);
        this.openTelemetry = GlobalOpenTelemetry.get();

        ReactiveAdapterRegistry reactiveAdapterRegistry = new ReactiveAdapterRegistry();
        KinoticUtil.registerVertxFutureAdapter(reactiveAdapterRegistry);
        this.argumentConverter = new JacksonRpcArgumentConverter(jsonMapper, reactiveAdapterRegistry, properties, securityContext);
        ExceptionConverter exceptionConverter = new JacksonExceptionConverter(properties, jsonMapper);
        RpcResponseConverter responseConverter = new RpcResponseConverterComposite()
                .addConverters(new JacksonRpcResponseConverter(jsonMapper, reactiveAdapterRegistry, properties, securityContext),
                               new TextRpcResponseConverter(),
                               new BinaryRpcResponseConverter(reactiveAdapterRegistry));
        this.returnValueHandlerFactory = new RpcReturnValueHandlerFactoryComposite()
                .addFactories(new CompletableFutureValueHandlerFactory(responseConverter, exceptionConverter),
                              new VertxFutureRpcReturnValueHandlerFactory(responseConverter, exceptionConverter),
                              new MonoRpcReturnValueHandlerFactory(responseConverter, exceptionConverter),
                              new FluxReturnValueHandlerFactory(responseConverter, exceptionConverter));
    }

    /**
     * A builder of the Jackson mapper the platform converts service arguments and replies with, as
     * {@code KinoticJacksonConfig} configures a cluster member's: the Vert.x types in their Vert.x wire format,
     * the platform's own value types, and no constructor parameter detection. A process adds the modules its
     * services' types need before building the mapper it gives the factory.
     * @return the builder
     */
    public static JsonMapper.Builder jsonMapperBuilder() {
        return JsonMapper.builder()
                         .addModule(new VertxJackson3Module())
                         .addModule(KinoticJacksonConfig.kinoticCoreModule())
                         .disable(MapperFeature.DETECT_PARAMETER_NAMES);
    }

    /**
     * @return the mapper the proxies this factory mints convert arguments and replies with
     */
    public JsonMapper jsonMapper() {
        return jsonMapper;
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
                                                  nodeName,
                                                  serviceInterface,
                                                  argumentConverter,
                                                  returnValueHandlerFactory,
                                                  transport,
                                                  securityContext,
                                                  serviceDirectoryProvider,
                                                  requestLivenessWatcher,
                                                  vertx,
                                                  Thread.currentThread().getContextClassLoader(),
                                                  openTelemetry,
                                                  traceLogFilter);
    }

}
