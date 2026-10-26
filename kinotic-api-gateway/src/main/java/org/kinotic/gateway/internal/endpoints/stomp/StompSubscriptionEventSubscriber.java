


package org.kinotic.gateway.internal.endpoints.stomp;

import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.TraceLogFilter;
import io.vertx.ext.stomp.lite.StompServerConnection;
import lombok.RequiredArgsConstructor;
import io.vertx.ext.stomp.lite.frame.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;


/**
 * Handles messages sent to the event bus for a stomp based subscription
 */
@RequiredArgsConstructor
public class StompSubscriptionEventSubscriber implements StompSubscriptionHandler {

    private static final Logger log = LoggerFactory.getLogger(StompSubscriptionEventSubscriber.class);

    private final String destination;
    private final String subscriptionId;
    private final StompServerConnection connection;
    private final ReplyFlowControl replyFlowControl;
    private final JsonMapper jsonMapper;
    private final TraceLogFilter traceLogFilter;

    @Override
    public void handleEvent(Event<byte[]> event) {
        try {
            Frame frame = GatewayUtils.eventToStompFrame(event);
            // Set Subscription ID header
            frame.getHeaders().put(Frame.SUBSCRIPTION, subscriptionId);
            // Re-expose the typed sender as a header so external clients (e.g. device RPC) still see it.
            if (event.sender() != null) {
                frame.getHeaders().put(EventConstants.SENDER_HEADER, jsonMapper.writeValueAsString(event.sender()));
            }

            if(log.isTraceEnabled()) {
                // The marker is the server's own logging bookkeeping, set only while trace logging
                // is on. It is read off the event, so dropping it here keeps it out of every frame
                // a client receives.
                frame.getHeaders().remove(EventConstants.TRACE_EXCLUDED_HEADER);
                if(!traceLogFilter.isExcluded(event)) {
                    log.trace("Sending Frame\n{}", frame);
                }
            }

            replyFlowControl.written(connection.write(frame), frameSize(frame), event.metadata().get(EventConstants.CORRELATION_ID_HEADER));

        } catch (Exception e) {
            log.error("Unexpected Error in Handler {}", e.getMessage(), e);
            log.error("Closing connection");
            connection.close();
        }
    }

    // approximates the encoded size without encoding the frame twice: it counts characters, not UTF-8 bytes, and ignores header escaping
    private static int frameSize(Frame frame) {
        int ret = frame.getCommand().name().length() + 3;
        for (Map.Entry<String, String> header : frame.getHeaders().entrySet()) {
            ret += header.getKey().length() + header.getValue().length() + 2;
        }
        if (frame.getBody() != null) {
            ret += frame.getBody().length();
        }
        return ret;
    }

    @Override
    public void handleError(Throwable throwable) {
        log.error("Error on event bus subscription for destination {}", destination, throwable);
    }

}
