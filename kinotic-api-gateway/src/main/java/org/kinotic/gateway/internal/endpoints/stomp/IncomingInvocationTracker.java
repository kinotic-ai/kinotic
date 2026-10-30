package org.kinotic.gateway.internal.endpoints.stomp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;
import org.kinotic.core.api.exceptions.RpcStreamOverflowException;
import org.kinotic.core.internal.utils.EventUtil;
import org.kinotic.gateway.internal.endpoints.Services;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The requests the client on this connection has sent into the cluster and is still waiting on. A request
 * is remembered when the client sends it, watched from the moment a node accepts it, and forgotten when its
 * reply comes back, the client cancels it, or the send fails. If the node serving a watched request leaves
 * the cluster, the client is answered with an {@link RpcServiceUnavailableException} in the reply's place.
 * Also holds the client's reply subscriptions, which are how replies reach it, and passes each stream value
 * through the connection's {@link ReplyFlowControl}: a stream that cannot be suspended is cancelled when its
 * client falls too far behind, and the client is answered with an {@link RpcStreamOverflowException}.
 *
 * Created by Navíd Mitchell 🤪 on 9/9/26.
 */
@Slf4j
@RequiredArgsConstructor
public class IncomingInvocationTracker {

    private final Services services;
    private final ReplyFlowControl replyFlowControl;
    // RequestLivenessWatcher is shared by every connection on this node and correlation ids are chosen by
    // clients, so this connection's watch keys carry a prefix of its own
    private final String watchKeyPrefix = UUID.randomUUID() + ":";
    private final Map<String, EventConsumer> replySubscriptions = new HashMap<>();
    // by correlation id; touched only on the connection's event loop
    private final Map<String, IncomingInvocation> awaitingReply = new HashMap<>();
    // streams the client cancelled, or the gateway cancelled because the client fell too far behind, whose
    // values in flight are dropped until the terminal reply passes through
    private final Set<String> cancelledStreams = new HashSet<>();

    /**
     * Subscribes the client to one of its reply destinations. A terminal reply passing through finishes the
     * request it answers.
     */
    public void subscribeReplies(CRI cri, String subscriptionIdentifier, StompSubscriptionHandler subscriptionHandler) {
        EventConsumer eventConsumer = services.eventBusService.listen(cri);
        eventConsumer.handler(event -> {
                         String correlationId = event.metadata().get(EventConstants.CORRELATION_ID_HEADER);
                         boolean terminal = EventUtil.isTerminalReply(event.metadata());
                         if (correlationId != null && cancelledStreams.contains(correlationId)) {
                             if (terminal) {
                                 cancelledStreams.remove(correlationId);
                                 subscriptionHandler.handleEvent(event);
                             } else {
                                 log.trace("Dropping a value of stream {}, which was cancelled", correlationId);
                             }
                         } else if (terminal) {
                             requestFinished(correlationId);
                             subscriptionHandler.handleEvent(event);
                         } else if (admit(correlationId, event)) {
                             subscriptionHandler.handleEvent(event);
                         }
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
            awaitingReply.put(correlationId, new IncomingInvocation(EventUtil.replyMetadataOf(request.metadata()), request.cri()));
            // a client may reuse the id of a request whose stream was cancelled
            cancelledStreams.remove(correlationId);
        }
    }

    /**
     * A node accepted the request: from here its reply depends on that node staying in the cluster.
     */
    public void requestAccepted(String correlationId, String nodeId, CRI destination) {
        if (correlationId != null) {
            // the reply can arrive before the acknowledgement; then there is nothing left to watch
            awaitingReply.computeIfPresent(correlationId, (_, invocation) -> {
                services.requestLivenessWatcher.watch(watchKeyPrefix + correlationId, nodeId, () -> nodeLeft(correlationId, destination, nodeId));
                return invocation;
            });
        }
    }

    /**
     * The client cancelled the request: it is forgotten, the values of its stream still in flight are dropped
     * until the terminal reply passes, and the destination the request was sent to, which the cancel is
     * published to, is emitted; null when the connection holds no request of that id.
     */
    public CRI cancel(String correlationId) {
        CRI ret = null;
        IncomingInvocation invocation = correlationId != null ? awaitingReply.get(correlationId) : null;
        if (invocation != null) {
            requestFinished(correlationId);
            cancelledStreams.add(correlationId);
            ret = invocation.getDestination();
        }
        return ret;
    }

    /**
     * The request is over: its reply arrived, the client cancelled it, or the send failed. Its node is no
     * longer watched.
     */
    public void requestFinished(String correlationId) {
        if (correlationId != null && awaitingReply.remove(correlationId) != null) {
            services.requestLivenessWatcher.unwatch(watchKeyPrefix + correlationId);
            replyFlowControl.forget(correlationId);
        }
    }

    /**
     * The connection closed: stops watching every request and drops every reply subscription.
     */
    public void dispose() {
        awaitingReply.keySet().forEach(correlationId -> services.requestLivenessWatcher.unwatch(watchKeyPrefix + correlationId));
        awaitingReply.clear();
        cancelledStreams.clear();
        replySubscriptions.values().forEach(EventConsumer::unregister);
        replySubscriptions.clear();
    }

    // the node serving the request left the cluster before replying: answer the client in its place
    private void nodeLeft(String correlationId, CRI destination, String nodeId) {
        IncomingInvocation invocation = awaitingReply.remove(correlationId);
        if (invocation != null) {
            replyFlowControl.forget(correlationId);
            RpcServiceUnavailableException cause = new RpcServiceUnavailableException(
                    "Node " + nodeId + " left the cluster while serving the request to " + destination.raw());
            try {
                services.eventBusService.send(services.exceptionConverter.convert(invocation.getReplyMetadata(), cause));
            } catch (Exception e) {
                log.error("Could not answer request {} to {} after node {} left", correlationId, destination.raw(), nodeId, e);
            }
        }
    }

    // a stream value the client is still waiting on goes through flow control; any other passes as it came
    private boolean admit(String correlationId, Event<byte[]> value) {
        boolean ret = true;
        IncomingInvocation invocation = correlationId != null ? awaitingReply.get(correlationId) : null;
        if (invocation != null && !replyFlowControl.admit(correlationId, invocation, value)) {
            ret = false;
            overflowed(correlationId, invocation);
        }
        return ret;
    }

    // the client is too far behind a stream that cannot be suspended: cancel it and answer the client in its place
    private void overflowed(String correlationId, IncomingInvocation invocation) {
        long behind = replyFlowControl.unsentBytes(correlationId);
        String destination = invocation.getDestination().raw();
        log.warn("Cancelled stream {} from {}: it cannot be suspended and {} bytes of it wait unsent",
                 correlationId, destination, behind);
        requestFinished(correlationId);
        cancelledStreams.add(correlationId);
        RpcStreamOverflowException cause = new RpcStreamOverflowException(
                "The stream from " + destination + " cannot be suspended and " + behind
                        + " bytes of it were still waiting to reach the client, so it was cancelled");
        try {
            replyFlowControl.cancel(correlationId, invocation);
            services.eventBusService.send(services.exceptionConverter.convert(invocation.getReplyMetadata(), cause));
        } catch (Exception e) {
            log.error("Could not cancel stream {} from {} after its client fell behind", correlationId, destination, e);
        }
    }
}
