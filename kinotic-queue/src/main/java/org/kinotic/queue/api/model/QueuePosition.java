package org.kinotic.queue.api.model;

/**
 * Where a record sits in a queue.
 *
 * @param queue  the queue name
 * @param shard  the shard the record's key hashes to
 * @param offset the record's position within its shard; offsets in a shard increase in the order records were
 *               appended, skipping the offsets taken by the shard's changes of owner
 */
public record QueuePosition(String queue, int shard, long offset) {
}
