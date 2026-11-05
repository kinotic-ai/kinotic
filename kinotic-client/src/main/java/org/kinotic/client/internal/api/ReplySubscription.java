package org.kinotic.client.internal.api;

import io.vertx.core.Future;
import org.kinotic.core.api.event.CRI;

/**
 * A proxy's reply subscription on one connection: its STOMP subscription id, the reply destination the gateway
 * authorized for the connection, and the receipt that completes once the gateway has registered the subscription
 * across its cluster, or fails when the connection ends first or the receipt is overdue.
 *
 * Created by Navíd Mitchell 🤪 on 11/5/26.
 */
record ReplySubscription(String id, CRI destination, Future<Void> receipt) {}
