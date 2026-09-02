


package org.kinotic.gateway.internal.endpoints.stomp;

import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.TraceLogFilter;
import io.vertx.ext.stomp.lite.StompServerConnection;
import io.vertx.ext.stomp.lite.frame.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;


/**
 * Handles messages sent to the event bus for a stomp based subscription
 */
public class StompSubscriptionEventSubscriber implements StompSubscriptionHandler {

    private static final Logger log = LoggerFactory.getLogger(StompSubscriptionEventSubscriber.class);

    private final String destination;
    private final String subscriptionId;
    private final StompServerConnection connection;
    private final JsonMapper jsonMapper;
    private final TraceLogFilter traceLogFilter;
    private final boolean serviceSubscription;

    public StompSubscriptionEventSubscriber(String destination,
                                            String subscriptionId,
                                            StompServerConnection connection,
                                            JsonMapper jsonMapper,
                                            TraceLogFilter traceLogFilter) {
        this.destination = destination;
        this.subscriptionId = subscriptionId;
        this.connection = connection;
        this.jsonMapper = jsonMapper;
        this.traceLogFilter = traceLogFilter;
        this.serviceSubscription = destination.startsWith(EventConstants.SERVICE_DESTINATION_SCHEME + ":");
    }

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

            // The trace verdict is the server's own logging bookkeeping, set only while trace
            // logging is on. Only a srv:// subscriber answers what it receives, so only it needs
            // the verdict to ride along on its reply; every other subscription ends the exchange
            // at this client, where it is noise the client has no business seeing.
            if(log.isTraceEnabled()) {
                if(!serviceSubscription) {
                    frame.getHeaders().remove(EventConstants.TRACE_EXCLUDED_HEADER);
                }
                if(!traceLogFilter.isExcluded(event)) {
                    log.trace("Sending Frame\n{}", frame);
                }
            }

            connection.write(frame);

        } catch (Exception e) {
            log.error("Unexpected Error in Handler {}", e.getMessage(), e);
            log.error("Closing connection");
            connection.close();
        }
    }

    @Override
    public void handleError(Throwable throwable) {
        log.error("Error on event bus subscription for destination {}", destination, throwable);
    }

}
