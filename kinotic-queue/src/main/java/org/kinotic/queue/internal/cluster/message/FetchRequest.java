package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Asks a node for a shard's entries starting at an offset. The reply is a {@link FetchResponse}.
 *
 * @param offset   the first offset to return
 * @param max      the most entries to return
 * @param maxBytes the size after which no further entry is returned; the entry at {@code offset} is returned however
 *                 large it is
 */
public record FetchRequest(ShardId shardId, long offset, int max, long maxBytes) {

    public static FetchRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new FetchRequest(wire.readShardId(), wire.readLong(), wire.readInt(), wire.readLong());
    }

    public Buffer toBuffer() {
        return Wire.appendShardId(Wire.buffer(), shardId).appendLong(offset).appendInt(max).appendLong(maxBytes);
    }
}
