package org.kinotic.core.api.event;

import io.vertx.core.Future;
import org.kinotic.core.api.service.RequestLivenessWatcher;

/**
 * What a service proxy needs from the transport it sends through: a consumer for the replies to its requests, a
 * request delivered to one instance of a service with an acknowledgement naming where it went, and a control event
 * published to every instance of the service. In a cluster member the {@link EventBusService} is the transport; a
 * process outside the cluster supplies one that reaches a gateway.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
public interface RpcTransport {

    /**
     * Sends an {@link Event} to one consumer of its destination, which must acknowledge receipt.
     * @param event to send
     * @return a {@link Future} that completes with the id of the node whose consumer received the event, which a
     *         {@link RequestLivenessWatcher} watches until the reply arrives, or fails when no consumer took it
     */
    Future<String> sendWithAck(Event<byte[]> event);

    /**
     * Publishes an {@link Event} to every consumer registered on the {@link CRI#baseResource()} of the
     * given {@link CRI}, wherever it is registered. Unlike {@link #sendWithAck(Event)}, which delivers to a single
     * consumer, publish is fan-out: every registered consumer receives its own copy. Fire-and-forget with
     * at-most-once delivery.
     * @param event to publish
     */
    void publish(Event<byte[]> event);

    /**
     * Creates a new {@link EventConsumer} that will receive {@link Event<byte[]>} sent to the
     * {@link CRI#baseResource()} of the given {@link CRI}.
     * The consumer is not registered until {@link EventConsumer#handler} is called.
     * Use {@link EventConsumer#completion()} to wait for registration to complete.
     * @param cri whose base resource will be subscribed to
     * @return the newly created {@link EventConsumer}
     */
    EventConsumer listen(CRI cri);

}
