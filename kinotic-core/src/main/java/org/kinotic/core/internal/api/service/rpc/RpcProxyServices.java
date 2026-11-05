package org.kinotic.core.internal.api.service.rpc;

import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Vertx;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.RpcTransport;
import org.kinotic.core.api.event.TraceLogFilter;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.service.RequestLivenessWatcher;

import java.util.function.Supplier;

/**
 * What every service proxy of a process shares, assembled once by the process and handed to each proxy beside what
 * differs per proxy.
 *
 * Created by Navíd Mitchell 🤪 on 11/05/26.
 */
@Getter
@RequiredArgsConstructor
public class RpcProxyServices {

    @NonNull
    private final String nodeName;
    @NonNull
    private final RpcReturnValueHandlerFactory returnValueHandlerFactory;
    @NonNull
    private final RpcTransport transport;
    /**
     * The participant an invocation is sent as, read when the invocation is made; a process that authenticates no
     * participant of its own supplies null.
     */
    @NonNull
    private final Supplier<Participant> callerParticipant;
    /**
     * The directory an unreachable service is reported to; a process holding none supplies null.
     */
    @NonNull
    private final Supplier<ServiceDirectory> serviceDirectory;
    @NonNull
    private final RequestLivenessWatcher requestLivenessWatcher;
    @NonNull
    private final Vertx vertx;
    @NonNull
    private final OpenTelemetry openTelemetry;
    @NonNull
    private final TraceLogFilter traceLogFilter;

}
