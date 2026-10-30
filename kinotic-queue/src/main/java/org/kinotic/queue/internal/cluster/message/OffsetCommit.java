package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Asks a node holding a copy of a shard to store a consumer's next offset on it.
 */
public record OffsetCommit(ShardId shardId, String consumerName, long nextOffset) {

    public static OffsetCommit fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new OffsetCommit(wire.readShardId(), wire.readString(), wire.readLong());
    }

    public Buffer toBuffer() {
        return Wire.appendString(Wire.appendShardId(Wire.buffer(), shardId), consumerName).appendLong(nextOffset);
    }
}
