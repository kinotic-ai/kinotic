package org.kinotic.stream.api.model;

/**
 * Where a record sits in a stream.
 *
 * @param stream the stream name
 * @param shard  the shard the record's key hashes to
 * @param offset the record's position within its shard; offsets in a shard start at zero and increase by one per record
 */
public record StreamPosition(String stream, int shard, long offset) {
}
