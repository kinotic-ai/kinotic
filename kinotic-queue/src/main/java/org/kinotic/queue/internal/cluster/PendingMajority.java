package org.kinotic.queue.internal.cluster;

import io.vertx.core.Promise;

/**
 * A request an owner holds until a majority of the shard's copies confirms it, failing it once its deadline passes.
 *
 * @param deadline the {@link MonotonicClock#millis()} reading after which the request fails
 */
record PendingMajority(Promise<Void> promise, long deadline) {
}
