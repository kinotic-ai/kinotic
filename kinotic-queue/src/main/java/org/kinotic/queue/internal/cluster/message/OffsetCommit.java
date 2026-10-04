package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Asks a node holding a copy of a shard to store a consumer's next offset on it.
 */
public record OffsetCommit(String queue, String consumerName, int shard, long nextOffset) {

    public static OffsetCommit fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new OffsetCommit(wire.readString(), wire.readString(), wire.readInt(), wire.readLong());
    }

    public Buffer toBuffer() {
        Buffer ret = Wire.appendString(Wire.buffer(), queue);
        return Wire.appendString(ret, consumerName).appendInt(shard).appendLong(nextOffset);
    }
}
