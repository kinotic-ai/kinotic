package org.kinotic.queue.internal.log;

/**
 * A record as a shard stores it.
 *
 * @param offset  the record's position in its shard
 * @param epoch   the ownership epoch of the node that first wrote the record
 * @param key     the key the record was appended with
 * @param payload the record content
 */
public record ShardEntry(long offset, long epoch, String key, byte[] payload) {
}
