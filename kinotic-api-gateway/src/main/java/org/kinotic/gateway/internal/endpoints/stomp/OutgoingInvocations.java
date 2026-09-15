package org.kinotic.gateway.internal.endpoints.stomp;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.ListenerStatus;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;
import org.kinotic.core.internal.utils.EventUtil;
import org.kinotic.gateway.internal.endpoints.Services;
import reactor.core.Disposable;

import java.util.HashMap;
import java.util.Map;

/**
 * The invocations delivered to the services the client on this connection publishes, until the client
 * sends the terminal reply. An invocation is remembered when it is delivered and forgotten when the
 * client's terminal reply passes through, when the requester cancels it, or when the requester of a
 * stream stops listening, in which case the client is told to stop. When the connection closes, every
 * invocation still awaiting a reply is answered with an {@link RpcServiceUnavailableException} on the
 * client's behalf.
 *
 * Created by Navíd Mitchell 🤪 on 9/9/26.
 */
@Slf4j
public class OutgoingInvocations {

    private final Services services;
    // by correlation id, until the client's terminal reply; touched only on the connection's event loop
    private final Map<String, OutgoingInvocation> awaitingReply = new HashMap<>();
    // one watch per streaming invocation, on its requester's reply destination
    private final Map<String, Disposable> requesterWatches = new HashMap<>();

    public OutgoingInvocations(Services services) {
        this.services = services;
    }

    /**
     * An invocation is being delivered to the client: remembers it until the client's terminal reply. A
     * cancel control forgets the invocation it names. An invocation without a correlation id or reply-to
     * expects no reply and is not remembered.
     * @param event the invocation
     * @param subscriptionHandler the subscription it is delivered through; a cancel goes back through the same one
     * @return true when the invocation now awaits the client's reply, so its replies are allowed until the terminal one
     */
    public boolean invocationDelivered(Event<byte[]> event, StompSubscriptionHandler subscriptionHandler) {
        boolean ret = false;
        Metadata metadata = event.metadata();
        String correlationId = metadata.get(EventConstants.CORRELATION_ID_HEADER);
        if (correlationId != null) {
            String control = metadata.get(EventConstants.CONTROL_HEADER);
            if (control == null) {
                if (metadata.contains(EventConstants.REPLY_TO_HEADER)) {
                    awaitingReply.put(correlationId, new OutgoingInvocation(event.cri(),
                                                                          EventUtil.replyMetadataOf(metadata),
                                                                          subscriptionHandler,
                                                                          services.vertx.getOrCreateContext()));
                    ret = true;
                }
            } else if (EventConstants.CONTROL_VALUE_CANCEL.equals(control)) {
                forget(correlationId);
            }
        }
        return ret;
    }

    /**
     * @return true when the reply answers an invocation still awaiting one and is addressed to that
     * invocation's reply destination
     */
    public boolean replyOwed(Event<byte[]> reply) {
        OutgoingInvocation invocation = awaitingReply.get(reply.metadata().get(EventConstants.CORRELATION_ID_HEADER));
        return invocation != null
                && reply.cri().raw().equals(CRI.create(invocation.replyMetadata().get(EventConstants.REPLY_TO_HEADER)).raw());
    }

    /**
     * The client sent a reply. A terminal reply finishes its invocation. A stream value starts a watch on
     * the requester's reply destination, so the stream can be stopped once nothing listens there.
     */
    public void replySent(Event<byte[]> reply) {
        String correlationId = reply.metadata().get(EventConstants.CORRELATION_ID_HEADER);
        if (correlationId != null) {
            if (EventUtil.isTerminalReply(reply.metadata())) {
                forget(correlationId);
            } else {
                OutgoingInvocation invocation = awaitingReply.get(correlationId);
                if (invocation != null) {
                    requesterWatches.computeIfAbsent(correlationId, _ -> watchRequester(correlationId, invocation));
                }
            }
        }
    }

    /**
     * The connection closed: stops every requester watch and answers every invocation still awaiting a
     * reply with an {@link RpcServiceUnavailableException}.
     */
    public void close() {
        requesterWatches.values().forEach(Disposable::dispose);
        requesterWatches.clear();
        awaitingReply.forEach((correlationId, invocation) -> {
            RpcServiceUnavailableException cause = new RpcServiceUnavailableException(
                    "The connection serving the request disconnected before replying");
            try {
                services.eventBusService.send(services.exceptionConverter.convert(invocation.replyMetadata(), cause));
            } catch (Exception e) {
                log.error("Could not answer invocation {} after its connection closed", correlationId, e);
            }
        });
        awaitingReply.clear();
    }

    private Disposable watchRequester(String correlationId, OutgoingInvocation invocation) {
        CRI replyCri = CRI.create(invocation.replyMetadata().get(EventConstants.REPLY_TO_HEADER));
        return services.eventBusService
                       .monitorListenerStatus(replyCri)
                       .subscribe(status -> {
                                      // arrives on the cluster manager's thread; the cancel frame must be
                                      // written on the connection's event loop
                                      if (status == ListenerStatus.INACTIVE) {
                                          invocation.context().runOnContext(_ -> requesterGone(correlationId));
                                      }
                                  },
                                  throwable -> log.warn("Requester watch for invocation {} failed", correlationId, throwable));
    }

    // Nothing listens on the requester's reply destination: forget the invocation and tell the client to stop
    // the stream. The requester is answered too, so one whose registration this node had not seen yet gets
    // the error instead of silence; a requester that is gone drops it with the rest.
    private void requesterGone(String correlationId) {
        OutgoingInvocation invocation = awaitingReply.get(correlationId);
        if (invocation != null) {
            forget(correlationId);
            Metadata metadata = Metadata.create(Map.of(EventConstants.CONTROL_HEADER, EventConstants.CONTROL_VALUE_CANCEL,
                                                       EventConstants.CORRELATION_ID_HEADER, correlationId));
            invocation.subscriptionHandler().handleEvent(Event.create(invocation.cri(), metadata, null));
            RpcServiceUnavailableException cause = new RpcServiceUnavailableException(
                    "No listener on the reply destination of the stream");
            try {
                services.eventBusService.send(services.exceptionConverter.convert(invocation.replyMetadata(), cause));
            } catch (Exception e) {
                log.error("Could not answer invocation {} after its requester stopped listening", correlationId, e);
            }
        }
    }

    private void forget(String correlationId) {
        awaitingReply.remove(correlationId);
        Disposable monitor = requesterWatches.remove(correlationId);
        if (monitor != null) {
            monitor.dispose();
        }
    }
}
