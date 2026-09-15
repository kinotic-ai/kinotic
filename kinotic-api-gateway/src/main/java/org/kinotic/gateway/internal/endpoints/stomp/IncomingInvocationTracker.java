package org.kinotic.gateway.internal.endpoints.stomp;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;
import org.kinotic.core.internal.utils.EventUtil;
import org.kinotic.gateway.internal.endpoints.Services;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The requests the client on this connection has sent into the cluster and is still waiting on. A request
 * is remembered when the client sends it, watched from the moment a node accepts it, and forgotten when its
 * reply comes back, the client cancels it, or the send fails. If the node serving a watched request leaves
 * the cluster, the client is answered with an {@link RpcServiceUnavailableException} in the reply's place.
 * Also holds the client's reply subscriptions, which are how replies reach it.
 *
 * Created by Navíd Mitchell 🤪 on 9/9/26.
 */
@Slf4j
public class IncomingInvocationTracker {

    private final Services services;
    // RequestLivenessWatcher is shared by every connection on this node and correlation ids are chosen by
    // clients, so this connection's watch keys carry a prefix of its own
    private final String watchKeyPrefix = UUID.randomUUID() + ":";
    private final Map<String, EventConsumer> replySubscriptions = new HashMap<>();
    // by correlation id: the reply-to headers an answer to the request needs; touched only on the connection's event loop
    private final Map<String, Metadata> awaitingReply = new HashMap<>();

    public IncomingInvocationTracker(Services services) {
        this.services = services;
    }

    /**
     * Subscribes the client to one of its reply destinations. A terminal reply passing through finishes the
     * request it answers.
     */
    public void subscribeReplies(CRI cri, String subscriptionIdentifier, StompSubscriptionHandler subscriptionHandler) {
        EventConsumer eventConsumer = services.eventBusService.listen(cri);
        eventConsumer.handler(event -> {
                         if (EventUtil.isTerminalReply(event.metadata())) {
                             requestFinished(event.metadata().get(EventConstants.CORRELATION_ID_HEADER));
                         }
                         subscriptionHandler.handleEvent(event);
                     })
                     .exceptionHandler(subscriptionHandler::handleError);
        // a subscription id the client reuses ends the consumer it named before
        EventConsumer previous = replySubscriptions.put(subscriptionIdentifier, eventConsumer);
        if (previous != null) {
            previous.unregister();
        }
    }

    /**
     * @return true when the identifier was one of the client's reply subscriptions
     */
    public boolean unsubscribeReplies(String subscriptionIdentifier) {
        EventConsumer consumer = replySubscriptions.remove(subscriptionIdentifier);
        if (consumer != null) {
            consumer.unregister();
        }
        return consumer != null;
    }

    /**
     * The client sent a request: remembers how to answer it. A request without a correlation id has no
     * reply to wait for.
     */
    public void requestSent(Event<byte[]> request) {
        String correlationId = request.metadata().get(EventConstants.CORRELATION_ID_HEADER);
        if (correlationId != null) {
            awaitingReply.put(correlationId, EventUtil.replyMetadataOf(request.metadata()));
        }
    }

    /**
     * A node accepted the request: from here its reply depends on that node staying in the cluster.
     */
    public void requestAccepted(String correlationId, String nodeId, CRI destination) {
        if (correlationId != null) {
            // the reply can arrive before the acknowledgement; then there is nothing left to watch
            awaitingReply.computeIfPresent(correlationId, (_, replyMetadata) -> {
                services.requestLivenessWatcher.watch(watchKeyPrefix + correlationId, nodeId, () -> nodeLeft(correlationId, destination, nodeId));
                return replyMetadata;
            });
        }
    }

    /**
     * The request is over: its reply arrived, the client cancelled it, or the send failed. Its node is no
     * longer watched.
     */
    public void requestFinished(String correlationId) {
        if (correlationId != null && awaitingReply.remove(correlationId) != null) {
            services.requestLivenessWatcher.unwatch(watchKeyPrefix + correlationId);
        }
    }

    /**
     * The connection closed: stops watching every request and drops every reply subscription.
     */
    public void dispose() {
        awaitingReply.keySet().forEach(correlationId -> services.requestLivenessWatcher.unwatch(watchKeyPrefix + correlationId));
        awaitingReply.clear();
        replySubscriptions.values().forEach(EventConsumer::unregister);
        replySubscriptions.clear();
    }

    // the node serving the request left the cluster before replying: answer the client in its place
    private void nodeLeft(String correlationId, CRI destination, String nodeId) {
        Metadata replyMetadata = awaitingReply.remove(correlationId);
        if (replyMetadata != null) {
            RpcServiceUnavailableException cause = new RpcServiceUnavailableException(
                    "Node " + nodeId + " left the cluster while serving the request to " + destination.raw());
            try {
                services.eventBusService.send(services.exceptionConverter.convert(replyMetadata, cause));
            } catch (Exception e) {
                log.error("Could not answer request {} to {} after node {} left", correlationId, destination.raw(), nodeId, e);
            }
        }
    }
}
