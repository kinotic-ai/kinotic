package org.kinotic.queue.internal.cluster;

import io.vertx.core.Context;
import io.vertx.core.Promise;

/**
 * An append waiting for its batch to be sent and answered.
 *
 * @param caller the context the append's result is completed on, or null when it was called outside Vert.x
 */
record QueuedAppend(String key, byte[] payload, Promise<Long> promise, Context caller) {
}
