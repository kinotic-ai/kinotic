package org.kinotic.core.api;

import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.RpcTransport;
import org.kinotic.core.api.event.TraceLogFilter;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.service.RequestLivenessWatcher;
import org.kinotic.core.internal.api.service.rpc.RpcArgumentConverterResolver;
import org.kinotic.core.internal.api.service.rpc.RpcProxyServices;
import org.kinotic.core.internal.api.service.rpc.RpcReturnValueHandlerFactory;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Mints the service proxies of a process: those a {@link ServiceRegistry} hands out to a cluster member, and those a
 * client outside the cluster calls a gateway through. The process's Spring context supplies what every proxy
 * shares, the conversion of arguments, replies and exceptions, the handling of every supported return type, the
 * Vert.x instance and the tracing of each invocation; the proxies over one transport are minted by the
 * {@link RpcServiceProxies} that {@link #over} returns.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@RequiredArgsConstructor
@Component
public class RpcServiceProxyFactory {

    private final RpcArgumentConverterResolver argumentConverters;
    private final RpcReturnValueHandlerFactory returnValueHandlerFactory;
    private final Vertx vertx;
    private final OpenTelemetry openTelemetry;
    private final TraceLogFilter traceLogFilter;

    /**
     * The proxies over a transport, in a process that authenticates no participant and holds no service directory.
     * @param transport              carries the proxies' requests and replies
     * @param requestLivenessWatcher fails a proxy's request when the receiver its acknowledgement named is gone
     * @param nodeName               names the process in the reply destinations of its proxies
     * @return the proxies' minter
     */
    public RpcServiceProxies over(RpcTransport transport, RequestLivenessWatcher requestLivenessWatcher, String nodeName) {
        return over(transport, requestLivenessWatcher, () -> null, () -> null, nodeName);
    }

    /**
     * The proxies over a transport.
     * @param transport              carries the proxies' requests and replies
     * @param requestLivenessWatcher fails a proxy's request when the receiver its acknowledgement named is gone
     * @param callerParticipant      the participant an invocation is sent as, read when the invocation is made; a
     *                               process that authenticates no participant supplies null
     * @param serviceDirectory       the directory an unreachable service is reported to; a process holding none
     *                               supplies null
     * @param nodeName               names the process in the reply destinations of its proxies
     * @return the proxies' minter
     */
    public RpcServiceProxies over(RpcTransport transport,
                                  RequestLivenessWatcher requestLivenessWatcher,
                                  Supplier<Participant> callerParticipant,
                                  Supplier<ServiceDirectory> serviceDirectory,
                                  String nodeName) {
        Validate.notNull(transport, "transport must not be null");
        Validate.notNull(requestLivenessWatcher, "requestLivenessWatcher must not be null");
        Validate.notBlank(nodeName, "nodeName must not be blank");
        return new RpcServiceProxies(argumentConverters,
                                     new RpcProxyServices(nodeName,
                                                          returnValueHandlerFactory,
                                                          transport,
                                                          callerParticipant,
                                                          serviceDirectory,
                                                          requestLivenessWatcher,
                                                          vertx,
                                                          openTelemetry,
                                                          traceLogFilter));
    }

}
