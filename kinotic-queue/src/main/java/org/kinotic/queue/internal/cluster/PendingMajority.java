package org.kinotic.queue.internal.cluster;

import io.vertx.core.Promise;

/**
 * A request an owner holds until a majority of the shard's copies confirms it, failing it once its deadline passes.
 *
 * @param deadline the time, in epoch milliseconds, after which the request fails
 */
record PendingMajority<T>(Promise<T> promise, long deadline) {
}
