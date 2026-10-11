package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Asks a node for a shard's entries starting at an offset. The reply is a {@link FetchResponse}.
 * The {@code incarnation} names the queue the request is about, so a request about a deleted queue is never applied to a
 * queue created later with the same name.
 *
 * @param offset   the first offset to return; -1 asks a shard's owner for its committed offset and no entries
 * @param max      the most entries to return
 * @param maxBytes the size after which no further entry is returned; the entry at {@code offset} is returned however
 *                 large it is
 */
public record FetchRequest(String queue, String incarnation, int shard, long offset, int max, long maxBytes) {

    public static FetchRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new FetchRequest(wire.readString(), wire.readString(), wire.readInt(), wire.readLong(), wire.readInt(), wire.readLong());
    }

    public Buffer toBuffer() {
        return Wire.appendString(Wire.appendString(Wire.buffer(), queue), incarnation).appendInt(shard).appendLong(offset).appendInt(max).appendLong(maxBytes);
    }
}
