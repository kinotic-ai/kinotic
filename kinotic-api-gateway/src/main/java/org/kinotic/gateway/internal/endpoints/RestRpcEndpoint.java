package org.kinotic.gateway.internal.endpoints;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.api.event.RpcRequestService;
import org.kinotic.core.api.event.ZonePartitioningService;
import org.kinotic.core.api.event.ZoneRulesResolver;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.exceptions.RpcMissingServiceException;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;
import org.kinotic.core.api.security.AuthenticationContext;
import org.kinotic.core.api.security.AuthenticationHandler;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.core.api.service.SuppliesGatewayRoutes;
import org.kinotic.gateway.api.utils.ApiGatewayUtil;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/**
 * Invokes published service functions over HTTP: {@code POST /api/srv/<cri>} calls the function the
 * {@link CRI} names, written without its {@code srv://} scheme and with a {@code #} before a version escaped as
 * {@code %23}, so {@code POST /api/srv/app.acme.orders~com.acme.OrderService/findById%231.0.0} calls
 * {@code srv://app.acme.orders~com.acme.OrderService/findById#1.0.0}.
 * <p>
 * The request authenticates from its headers exactly as a STOMP {@code CONNECT} does, and may address only the
 * zones a STOMP connection of the same participant may send to. The body is the function's arguments, a JSON
 * array with {@code Content-Type: application/json} or an object keyed by parameter name with
 * {@code Content-Type: application/x-kinotic-named-json}, and a W3C {@code traceparent} continues the caller's
 * trace. The response is the function's single result with the content type the service replied with.
 * A function that failed answers {@code 500}, a destination nothing serves {@code 404}, and a service that left
 * before answering {@code 503}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestRpcEndpoint implements SuppliesGatewayRoutes {

    private static final String ROUTE_PREFIX = "/api/srv/";
    private static final List<String> ACCEPTED_CONTENT_TYPES = List.of(EventConstants.CONTENT_TYPE_JSON,
                                                                       EventConstants.CONTENT_TYPE_NAMED_JSON);
    private static final List<String> FORWARDED_HEADERS = List.of(EventConstants.TRACEPARENT_HEADER,
                                                                  EventConstants.TRACESTATE_HEADER);

    private final SecurityService securityService;
    private final SecurityContext securityContext;
    private final RpcRequestService rpcRequestService;
    private final ZoneRulesResolver zoneRulesResolver;
    private final ZonePartitioningService zonePartitioningService;

    @Override
    public void mountRoutes(Router router) {
        router.post(ROUTE_PREFIX + "*").handler(new AuthenticationHandler(securityService,
                                                                          securityContext,
                                                                          new AuthenticationContext(AuthenticationContext.PUBLISHED_SERVICES)));
        router.post(ROUTE_PREFIX + "*").handler(this::invoke);
    }

    private void invoke(RoutingContext ctx) {
        Participant participant = ctx.get(EventConstants.SENDER_HEADER);
        String contentType = mediaType(ctx.request().getHeader(HttpHeaders.CONTENT_TYPE));
        CRI cri = criOf(ctx.request().path());

        if (!ACCEPTED_CONTENT_TYPES.contains(contentType)) {
            ctx.response().setStatusCode(415).end();
        } else if (cri == null) {
            ctx.fail(new IllegalArgumentException("The path must name a service function: " + ROUTE_PREFIX + "<cri without srv://>"));
        } else if (!zoneRulesResolver.resolve(participant).restrictedTo(zonePartitioningService).sendAllowed(cri)) {
            ctx.fail(new AuthorizationException("Not Authorized to send to " + cri.raw()));
        } else {
            Metadata metadata = Metadata.create();
            metadata.put(EventConstants.CONTENT_TYPE_HEADER, contentType);
            for (String header : FORWARDED_HEADERS) {
                String value = ctx.request().getHeader(header);
                if (value != null) {
                    metadata.put(header, value);
                }
            }
            byte[] body = ctx.body().length() > 0 ? ctx.body().buffer().getBytes() : emptyArguments(contentType);
            rpcRequestService.request(Event.create(cri, metadata, body, participant))
                             .onSuccess(reply -> respond(ctx, reply))
                             .onFailure(throwable -> fail(ctx, throwable));
        }
    }

    private static void respond(RoutingContext ctx, Event<byte[]> reply) {
        if (ctx.response().closed()) {
            return;
        }
        String error = reply.metadata().get(EventConstants.ERROR_HEADER);
        if (error != null) {
            ApiGatewayUtil.writeException(ctx, new IllegalStateException(error));
        } else {
            String replyType = reply.metadata().get(EventConstants.CONTENT_TYPE_HEADER);
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, replyType != null ? replyType : EventConstants.CONTENT_TYPE_JSON);
            byte[] data = reply.data();
            ctx.response().end(data != null ? Buffer.buffer(data) : Buffer.buffer());
        }
    }

    private static void fail(RoutingContext ctx, Throwable throwable) {
        if (ctx.response().closed()) {
            return;
        }
        int status;
        if (throwable instanceof RpcMissingServiceException) {
            status = 404;
        } else if (throwable instanceof RpcServiceUnavailableException) {
            status = 503;
        } else {
            log.warn("REST invocation failed", throwable);
            status = 500;
        }
        ctx.fail(status, throwable);
    }

    // the path is percent-decoded here rather than by the router, so an escaped '#' survives as the version separator
    private static CRI criOf(String rawPath) {
        CRI ret = null;
        String encoded = rawPath.substring(ROUTE_PREFIX.length());
        if (!encoded.isBlank()) {
            String decoded = URI.create("http://localhost/" + encoded).getPath().substring(1);
            CRI cri = CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME + "://" + decoded);
            if (cri.hasPath()) {
                ret = cri;
            }
        }
        return ret;
    }

    private static String mediaType(String contentType) {
        String ret = null;
        if (contentType != null) {
            int parameters = contentType.indexOf(';');
            ret = (parameters >= 0 ? contentType.substring(0, parameters) : contentType).trim().toLowerCase();
        }
        return ret;
    }

    private static byte[] emptyArguments(String contentType) {
        return (EventConstants.CONTENT_TYPE_JSON.equals(contentType) ? "[]" : "{}").getBytes();
    }
}
