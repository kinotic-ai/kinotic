package org.kinotic.queue.internal.cluster;

import io.vertx.core.Promise;
import org.kinotic.queue.internal.cluster.message.FetchResponse;

/**
 * A fetch an owner holds until the shard's committed offset passes {@code from}.
 */
record PendingFetch(long from, int max, long maxBytes, Promise<FetchResponse> promise) {
}
