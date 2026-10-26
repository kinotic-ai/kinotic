

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
    private final ReplyFlowControl replyFlowControl;
    private final JsonMapper jsonMapper;
    private final TraceLogFilter traceLogFilter;


    public DefaultStompServerHandler(Services services) {
        this.replyFlowControl = new ReplyFlowControl(services);
        this.endpointConnectionHandler = new EndpointConnectionHandler(services, replyFlowControl);
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
            if(traceLogFilter.isExcluded(frame.getDestination())){
                // The reply comes back on the client's reply destination, which names no service to
                // match, so the exclusion is marked here and rides back on the reply, which persists
                // every __ header of the request it answers
                frame.getHeaders().put(EventConstants.TRACE_EXCLUDED_HEADER, "true");
            }else{
                log.trace("Send Frame received\n{}", frame.toString());
            }
        }

        Event<byte[]> incomingEvent = new FrameEventAdapter(frame);

        endpointConnectionHandler
                .send(incomingEvent)
                .onComplete(ar -> {
                    if(ar.failed()){
                        failConnection("send to " + frame.getDestination() + " failed", ar.cause(), frame);
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

            StompSubscriptionEventSubscriber subscriber = new StompSubscriptionEventSubscriber(cri.raw(), subscriptionId, stompServerConnection, replyFlowControl, jsonMapper, traceLogFilter);
            // the receipt tells the client its subscription reaches it from every node, so a request it then
            // makes cannot have its reply sent before the reply destination is known cluster-wide
            endpointConnectionHandler.subscribe(cri, subscriptionId, subscriber)
                                     .onSuccess(v -> stompServerConnection.sendReceiptIfNeeded(frame))
                                     .onFailure(cause -> failConnection("subscribe to " + frame.getDestination() + " failed", cause, frame));

        } catch (Exception e) {
            failConnection("subscribe to " + frame.getDestination() + " failed", e, frame);
        }
    }

    @Override
    public void unsubscribe(Frame frame) {
        log.trace("Unsubscribe Frame received\n{}", frame.toString());

        try {
            String subscriptionId = frame.getHeader(Frame.ID);

            endpointConnectionHandler.unsubscribe(subscriptionId);
            stompServerConnection.sendReceiptIfNeeded(frame);

        } catch (Exception e) {
            failConnection("unsubscribe " + frame.getHeader(Frame.ID) + " failed", e, frame);
        }
    }

    /**
     * Logs the failure, then sends an ERROR frame naming the frame and closes the connection — the
     * STOMP-mandated response to any frame the server cannot process.
     */
    private void failConnection(String detail, Throwable cause, Frame frame) {
        log.error("Terminating STOMP connection: {}", detail, cause);
        stompServerConnection.sendErrorAndDisconnect(cause, frame);
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
        // closed() follows a DISCONNECT frame and ends the session with the connection
    }

    @Override
    public void closed() {
        // An ACTIVITY or CONNECTION session outlives the connection so a reconnect on the same cookie resumes
        // it; a NONE session ends with the connection. Every request the connection had in flight ends here.
        endpointConnectionHandler.shutdown();
    }

}
