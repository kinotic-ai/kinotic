package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Asks a node holding a copy of a shard to store a consumer's next offset on it.
 * The {@code incarnation} names the queue the request is about, so a request about a deleted queue is never applied to a
 * queue created later with the same name.
 */
public record OffsetCommit(String queue, String incarnation, String consumerName, int shard, long nextOffset) {

    public static OffsetCommit fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new OffsetCommit(wire.readString(), wire.readString(), wire.readString(), wire.readInt(), wire.readLong());
    }

    public Buffer toBuffer() {
        Buffer ret = Wire.appendString(Wire.appendString(Wire.buffer(), queue), incarnation);
        return Wire.appendString(ret, consumerName).appendInt(shard).appendLong(nextOffset);
    }
}
