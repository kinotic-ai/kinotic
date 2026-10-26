package org.kinotic.client.internal.api;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.stomp.lite.client.StompClientConnection;
import io.vertx.ext.stomp.lite.client.StompErrorException;
import io.vertx.ext.stomp.lite.frame.Frame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.api.event.RpcTransport;
import org.kinotic.core.api.exceptions.RpcInvocationException;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;
import org.kinotic.core.api.utils.KinoticUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The {@link RpcTransport} of a gateway connection: requests go out as STOMP SEND frames and replies come back
 * through the connection's reply subscriptions. Every proxy's reply address is subscribed on each connection under
 * a destination the gateway authorizes for it, built from the connection's reply-to id, and the reply-to of every
 * request is rewritten to it; a request waits for the gateway's receipt of that subscription, so its reply is
 * never sent before the reply destination is known across the cluster. A request is acknowledged by the gateway's
 * RECEIPT for its frame, which the gateway writes once a node of its cluster has taken the request; the
 * acknowledgement names the connection, and a request still pending when the connection ends fails. The
 * connection's state is only touched on its context.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Slf4j
@RequiredArgsConstructor
public class StompRpcTransport implements RpcTransport {

    // the scope discriminator of the reply-to a control event carries, which nothing is subscribed under
    private static final String CONTROL_REPLY_DISCRIMINATOR = "control";

    private final Context context;
    private final String serverUrl;
    private final String nodeName;
    // by the address a proxy listens on, which its requests name as their reply-to
    private final Map<String, StompReplyConsumer> consumers = new HashMap<>();
    private StompClientConnection connection;
    private String connectionId;
    private String replyToId;
    private String controlReplyTo;

    /**
     * A connection was accepted: every consumer with a handler is subscribed on it.
     * @param connection the accepted connection
     * @param connectionId the id acknowledgements on this connection complete with
     * @param replyToId the reply-to id the gateway issued for the connection
     */
    void connected(StompClientConnection connection, String connectionId, String replyToId) {
        this.connection = connection;
        this.connectionId = connectionId;
        this.replyToId = replyToId;
        this.controlReplyTo = CRI.create(EventConstants.REPLY_DESTINATION_SCHEME,
                                         replyToId + ":" + CONTROL_REPLY_DISCRIMINATOR,
                                         nodeName).raw();
        for (StompReplyConsumer consumer : consumers.values()) {
            if (consumer.hasHandler()) {
                subscribe(consumer);
            }
        }
    }

    /**
     * The connection ended, and the gateway released its subscriptions with it.
     */
    void disconnected() {
        connection = null;
        connectionId = null;
        consumers.values().forEach(StompReplyConsumer::unsubscribed);
    }

    @Override
    public EventConsumer listen(CRI cri) {
        StompReplyConsumer consumer = new StompReplyConsumer(cri, this);
        runOnContext(() -> consumers.put(cri.raw(), consumer));
        return consumer;
    }

    @Override
    public Future<String> sendWithAck(Event<byte[]> event) {
        CompletableFuture<String> result = new CompletableFuture<>();
        runOnContext(() -> {
            if (connection == null) {
                result.completeExceptionally(new RpcServiceUnavailableException(
                        "Not connected to " + serverUrl + ", the request to " + event.cri().raw() + " was not sent"));
            } else {
                try {
                    StompReplyConsumer consumer = replyConsumerOf(event);
                    Map<String, String> headers = requestHeaders(event, consumer);
                    headers.put(Frame.RECEIPT, UUID.randomUUID().toString());
                    String acknowledgedBy = connectionId;
                    StompClientConnection sentOn = connection;
                    consumer.ready()
                            .compose(v -> sentOn.send(event.cri().raw(), headers, body(event)))
                            .onSuccess(v -> result.complete(acknowledgedBy))
                            .onFailure(cause -> result.completeExceptionally(mapSendFailure(cause, event.cri())));
                } catch (Exception e) {
                    result.completeExceptionally(e);
                }
            }
        });
        // the proxy composes on the result where it made the request
        return KinoticUtil.toFuture(result);
    }

    @Override
    public void publish(Event<byte[]> event) {
        runOnContext(() -> {
            if (connection == null) {
                log.debug("Not connected to {}, dropping the control event for {}", serverUrl, event.cri());
            } else {
                try {
                    connection.send(event.cri().raw(), requestHeaders(event, null), body(event))
                              .onFailure(cause -> log.debug("Could not send the control event for {}", event.cri(), cause));
                } catch (Exception e) {
                    log.warn("Could not send the control event for {}", event.cri(), e);
                }
            }
        });
    }

    // a consumer is subscribed once it has a handler, like a Vert.x consumer registers on its first handler
    void consumerReady(StompReplyConsumer consumer) {
        runOnContext(() -> {
            if (connection != null && consumer.destination() == null) {
                subscribe(consumer);
            }
        });
    }

    Future<Void> remove(StompReplyConsumer consumer) {
        Promise<Void> promise = Promise.promise();
        runOnContext(() -> {
            consumers.remove(consumer.listenAddress().raw());
            Future<Void> ret;
            if (connection != null && consumer.subscriptionId() != null) {
                ret = connection.unsubscribe(consumer.subscriptionId());
            } else {
                ret = Future.succeededFuture();
            }
            consumer.unsubscribed();
            ret.onComplete(promise);
        });
        return promise.future();
    }

    // A fresh destination per connection: its scope is the connection's reply-to id, which the gateway requires,
    // followed by a discriminator of this subscription's own, which doubles as the STOMP subscription id and as
    // the receipt the gateway answers once the subscription is registered across its cluster
    private void subscribe(StompReplyConsumer consumer) {
        String subscriptionId = UUID.randomUUID().toString();
        CRI address = consumer.listenAddress();
        CRI destination = CRI.create(EventConstants.REPLY_DESTINATION_SCHEME,
                                     replyToId + ":" + subscriptionId,
                                     address.resourceName(),
                                     address.path(),
                                     address.version());
        Future<Void> receipt = connection.subscribe(subscriptionId,
                                                    destination.raw(),
                                                    Map.of(Frame.RECEIPT, subscriptionId),
                                                    frame -> consumer.deliver(toEvent(frame)));
        consumer.subscribed(subscriptionId, destination, receipt);
    }

    /**
     * The consumer a request's reply-to names, subscribed on this connection.
     * @throws IllegalStateException when no consumer listens on the reply-to, since the gateway ends the connection
     *                               over a reply-to outside its scope
     */
    private StompReplyConsumer replyConsumerOf(Event<byte[]> event) {
        String replyTo = event.metadata().get(EventConstants.REPLY_TO_HEADER);
        StompReplyConsumer ret = replyTo != null ? consumers.get(replyTo) : null;
        if (ret == null || ret.destination() == null) {
            throw new IllegalStateException("No reply subscription on " + serverUrl + " for " + replyTo);
        }
        return ret;
    }

    /**
     * The STOMP headers of a request: its metadata, with the reply-to rewritten from the address a proxy listens on
     * to the consumer's destination on this connection. A control event names no reply-to of its own and gets one
     * the gateway accepts, since the gateway refuses every service send without one.
     * @param consumer the consumer the request's reply-to names, or null for a control event
     */
    private Map<String, String> requestHeaders(Event<byte[]> event, StompReplyConsumer consumer) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : event.metadata()) {
            headers.put(entry.getKey(), entry.getValue());
        }
        headers.put(EventConstants.REPLY_TO_HEADER, consumer != null ? consumer.destination().raw() : controlReplyTo);
        return headers;
    }

    private Buffer body(Event<byte[]> event) {
        return event.data() != null ? Buffer.buffer(event.data()) : null;
    }

    private Event<byte[]> toEvent(Frame frame) {
        Metadata metadata = Metadata.create(frame.getHeaders());
        byte[] data = frame.getBody() != null ? frame.getBody().getBytes() : null;
        return Event.create(CRI.create(frame.getDestination()), metadata, data);
    }

    // The gateway answers a request it refuses, such as one to a zone the participant may not address, with an
    // ERROR frame naming the request's receipt, so only the refused request fails with the error; every other
    // failure means the connection ended before the gateway acknowledged the request
    private Throwable mapSendFailure(Throwable cause, CRI destination) {
        Throwable ret;
        if (cause instanceof StompErrorException error) {
            ret = new RpcInvocationException(error.getMessage());
        } else {
            ret = new RpcServiceUnavailableException("The connection to " + serverUrl + " ended before the request to "
                                                             + destination.raw() + " was acknowledged", cause);
        }
        return ret;
    }

    private void runOnContext(Runnable task) {
        if (Vertx.currentContext() == context) {
            task.run();
        } else {
            context.runOnContext(v -> task.run());
        }
    }

}
