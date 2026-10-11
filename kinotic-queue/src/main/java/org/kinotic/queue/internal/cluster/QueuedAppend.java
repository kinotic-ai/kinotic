package org.kinotic.queue.internal.cluster;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.kinotic.queue.api.model.QueuePosition;

/**
 * An append waiting for its queue's lookup, then for its batch to be sent and answered.
 *
 * @param caller the context the append's result is completed on, or null when it was called outside Vert.x
 */
record QueuedAppend(Future<StoredQueue> queue, String key, byte[] payload, Promise<QueuePosition> promise, Context caller) {
}
