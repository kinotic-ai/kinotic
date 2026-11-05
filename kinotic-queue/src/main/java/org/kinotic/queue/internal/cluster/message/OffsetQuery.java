package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Asks a node holding a copy of a shard for a consumer's next offset on it. The reply is the offset, zero when
 * the node has none stored.
 */
public record OffsetQuery(ShardId shardId, String consumerName) {

    public static OffsetQuery fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new OffsetQuery(wire.readShardId(), wire.readString());
    }

    public Buffer toBuffer() {
        return Wire.appendString(Wire.appendShardId(Wire.buffer(), shardId), consumerName);
    }
}
