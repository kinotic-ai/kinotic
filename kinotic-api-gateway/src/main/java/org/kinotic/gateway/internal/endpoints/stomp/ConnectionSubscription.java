package org.kinotic.gateway.internal.endpoints.stomp;

import io.vertx.core.Future;
import org.kinotic.core.api.event.EventConsumer;

/**
 * One subscription of a connection: the consumer delivering to it, and the future its SUBSCRIBE receipt follows,
 * which settles once the consumer is registered across the cluster and every consumer the subscription's id
 * displaced is unregistered.
 *
 * @param consumer   delivers the subscription's events to the connection
 * @param subscribed settles when the subscription is in effect
 *
 * Created by Navíd Mitchell 🤪 on 11/8/26.
 */
record ConnectionSubscription(EventConsumer consumer, Future<Void> subscribed) {
}
