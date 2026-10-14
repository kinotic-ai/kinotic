package org.kinotic.queue.internal.cluster;

import io.vertx.core.Future;

import java.util.List;

/**
 * The last batch a producer sent to a shard, as its owner knows it.
 *
 * @param sequence the batch's sequence
 * @param written  the offset of each of the batch's records once they are written, -1 for a record the shard does not
 *                 hold
 * @param lastUsed when the producer last sent the batch, in epoch milliseconds
 */
record ProducerBatch(long sequence, Future<List<Long>> written, long lastUsed) {
}
