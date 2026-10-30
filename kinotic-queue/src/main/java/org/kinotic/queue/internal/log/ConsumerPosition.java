package org.kinotic.queue.internal.log;

/**
 * Where a consumer or worker group stands on one shard.
 *
 * @param nextOffset     the next offset to deliver
 * @param lastUsedMillis when the position was last committed or refreshed, in epoch milliseconds; the position expires
 *                       once it goes unused for the queue's retention period
 */
public record ConsumerPosition(long nextOffset, long lastUsedMillis) {
}
