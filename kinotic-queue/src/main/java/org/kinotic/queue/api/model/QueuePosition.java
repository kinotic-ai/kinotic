package org.kinotic.queue.api.model;

/**
 * Where a record sits in a queue.
 *
 * @param queue  the queue name
 * @param shard  the shard the record's key hashes to
 * @param offset the record's position within its shard; offsets in a shard start at zero and increase by one per record
 */
public record QueuePosition(String queue, int shard, long offset) {
}
