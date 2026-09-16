package org.kinotic.gateway.internal.endpoints.stomp;

import io.vertx.core.Context;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Metadata;

/**
 * One invocation delivered to a service the client publishes: the address it went to, the metadata its
 * replies carry, the subscription it went through, and the connection's Vert.x context.
 *
 * Created by Navíd Mitchell 🤪 on 9/10/26.
 */
public record OutgoingInvocation(CRI cri,
                                  Metadata replyMetadata,
                                  StompSubscriptionHandler subscriptionHandler,
                                  Context context) {
}
