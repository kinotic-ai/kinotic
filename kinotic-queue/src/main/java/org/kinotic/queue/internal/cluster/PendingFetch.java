package org.kinotic.queue.internal.cluster;

import io.vertx.core.Promise;
import org.kinotic.queue.internal.cluster.message.FetchResponse;

/**
 * A fetch an owner holds until the shard's committed offset passes {@code from}.
 *
 * @param timerId the timer that answers the fetch with no entries once it was held long enough
 */
record PendingFetch(long from, int max, long maxBytes, Promise<FetchResponse> promise, long timerId) {
}
