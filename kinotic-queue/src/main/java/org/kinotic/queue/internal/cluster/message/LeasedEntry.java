package org.kinotic.queue.internal.cluster.message;

import org.kinotic.queue.internal.log.ShardEntry;

/**
 * A record leased to a worker.
 *
 * @param entry         the record as the shard stores it
 * @param deliveryCount how many times the record has been leased to the group, including this time
 */
public record LeasedEntry(ShardEntry entry, int deliveryCount) {
}
