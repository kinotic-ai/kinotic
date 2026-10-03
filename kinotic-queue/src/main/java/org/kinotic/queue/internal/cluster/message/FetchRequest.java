package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Asks a node for a shard's entries starting at an offset. The reply is a {@link FetchResponse}.
 *
 * @param offset the first offset to return; -1 asks a shard's owner to start at its committed offset
 * @param max    the most entries to return
 */
public record FetchRequest(String queue, int shard, long offset, int max) {

    public static FetchRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new FetchRequest(wire.readString(), wire.readInt(), wire.readLong(), wire.readInt());
    }

    public Buffer toBuffer() {
        return Wire.appendString(Buffer.buffer(), queue).appendInt(shard).appendLong(offset).appendInt(max);
    }
}
