package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Asks a node holding a copy of a shard for a consumer's next offset on it. The reply is the offset, zero when
 * the node has none stored.
 */
public record OffsetQuery(String queue, String consumerName, int shard) {

    public static OffsetQuery fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new OffsetQuery(wire.readString(), wire.readString(), wire.readInt());
    }

    public static Buffer encodeReply(long nextOffset) {
        return Wire.buffer().appendLong(nextOffset);
    }

    public static long decodeReply(Buffer buffer) {
        return new Wire(buffer).readLong();
    }

    public Buffer toBuffer() {
        Buffer ret = Wire.appendString(Wire.buffer(), queue);
        return Wire.appendString(ret, consumerName).appendInt(shard);
    }
}
