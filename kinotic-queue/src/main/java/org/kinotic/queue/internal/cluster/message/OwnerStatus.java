package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Whether a queue node owns a shard, and how far it has committed it.
 *
 * @param active          whether the node owns the shard and serves it
 * @param committedOffset the offset before which the node has committed every record; -1 when it is not active
 */
public record OwnerStatus(boolean active, long committedOffset) {

    public static OwnerStatus fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new OwnerStatus(wire.readByte() == 1, wire.readLong());
    }

    public Buffer toBuffer() {
        return Wire.buffer().appendByte((byte) (active ? 1 : 0)).appendLong(committedOffset);
    }
}
