

package org.kinotic.gateway.internal.endpoints.stomp;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.ext.stomp.lite.AbstractStompServerHandler;
import io.vertx.ext.stomp.lite.frame.Frame;
import io.vertx.ext.stomp.lite.frame.InvalidConnectFrame;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.TraceLogFilter;
import org.kinotic.gateway.internal.endpoints.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 *
 * Created by Navid Mitchell on 2019-02-05.
 */
public class DefaultStompServerHandler extends AbstractStompServerHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultStompServerHandler.class);

    private final EndpointConnectionHandler endpointConnectionHandler;
    private final JsonMapper jsonMapper;
    private final TraceLogFilter traceLogFilter;


    public DefaultStompServerHandler(Services services) {
        this.endpointConnectionHandler = new EndpointConnectionHandler(services);
        this.jsonMapper = services.jsonMapper;
        this.traceLogFilter = services.traceLogFilter;
    }

    @Override
    public Future<MultiMap> handshake(RoutingContext routingContext) {
        return endpointConnectionHandler.handshake(routingContext);
    }

    @Override
    public Future<Map<String, String>> connect(Map<String, String> connectHeaders) {
        return endpointConnectionHandler.connect(connectHeaders);
    }

    @Override
    public void send(Frame frame) {
        // FIXME: this is probably the wrong way to  do this, We are not really providing guaranteed delivery below so this kinda just creates a bottle neck for no reason.
        // We pause the client to effectively make all client requests block until the previous request is handled asynchronously
        stompServerConnection.pause();

        if(log.isTraceEnabled()){
            boolean excluded = traceLogFilter.isExcluded(frame.getDestination());
            if(!excluded){
                log.trace("Send Frame received\n{}", frame.toString());
            }
            // The reply this request produces is addressed to the client's reply destination, which
            // no pattern is written for, so the verdict is recorded here and rides back on the reply,
            // which persists every __ header of the request that produced it
            frame.getHeaders().put(EventConstants.TRACE_EXCLUDED_HEADER, Boolean.toString(excluded));
        }

        Event<byte[]> incomingEvent = new FrameEventAdapter(frame);

        endpointConnectionHandler
                .send(incomingEvent)
                .onComplete(ar -> {
                    if(ar.failed()){
                        failConnection("send to " + frame.getDestination() + " failed", ar.cause());
                    }else{
                        stompServerConnection.sendReceiptIfNeeded(frame);
                        stompServerConnection.resume();
                    }
                });
    }

    @Override
    public void subscribe(Frame frame) {
        log.trace("Subscribe Frame received\n{}", frame.toString());

        try {

            String subscriptionId = frame.getHeader(Frame.ID);
            Assert.hasText(subscriptionId,"Subscription requests must contain an Id header");

            CRI cri = CRI.create(frame.getDestination());

            StompSubscriptionEventSubscriber subscriber = new StompSubscriptionEventSubscriber(cri.raw(), subscriptionId, stompServerConnection, jsonMapper, traceLogFilter);
            endpointConnectionHandler.subscribe(cri, subscriptionId, subscriber);

        } catch (Exception e) {
            failConnection("subscribe to " + frame.getDestination() + " failed", e);
        }
    }

    @Override
    public void unsubscribe(Frame frame) {
        log.trace("Unsubscribe Frame received\n{}", frame.toString());

        try {
            String subscriptionId = frame.getHeader(Frame.ID);

            endpointConnectionHandler.unsubscribe(subscriptionId);

        } catch (Exception e) {
            failConnection("unsubscribe " + frame.getHeader(Frame.ID) + " failed", e);
        }
    }

    /**
     * Logs the failure, then sends an ERROR frame and closes the connection — the
     * STOMP-mandated response to any frame the server cannot process.
     */
    private void failConnection(String detail, Throwable cause) {
        log.error("Terminating STOMP connection: {}", detail, cause);
        stompServerConnection.sendErrorAndDisconnect(cause);
    }

    @Override
    public void begin(Frame frame) {
        log.debug("begin Frame received\n{}", frame.toString());
    }

    @Override
    public void abort(Frame frame) {
        log.debug("abort Frame received\n{}", frame.toString());
    }

    @Override
    public void commit(Frame frame) {
        log.debug("commit Frame received\n{}", frame.toString());
    }

    @Override
    public void ack(Frame frame) {
        log.debug("ack Frame received\n{}", frame.toString());
    }

    @Override
    public void nack(Frame frame) {
        log.debug("nack Frame received\n{}", frame.toString());
    }

    @Override
    public void exception(Throwable t) {
        // TODO: Add support for auto blacklisting client
        if(t instanceof InvalidConnectFrame){
            log.error("Invalid connect frame {}\nFrame: {}", t.getMessage(), ((InvalidConnectFrame) t).getData().toString());
        }else{
            log.error("Client Caused Exception", t);
        }
    }

    @Override
    public void disconnected() {
        // we remove the session since the client disconnected on purpose
        endpointConnectionHandler.removeSession();
    }

    @Override
    public void closed() {
        // We don't remove the session if disconnect was not called because this could be a network issue
        endpointConnectionHandler.shutdown();
    }

}
