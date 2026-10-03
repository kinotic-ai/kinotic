package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;
import org.kinotic.queue.internal.log.ShardEntry;

import java.util.List;

/**
 * A shard's entries returned for a {@link FetchRequest}.
 *
 * @param entries    consecutive entries, possibly none
 * @param nextOffset the offset to request next
 */
public record FetchResponse(List<ShardEntry> entries, long nextOffset) {

    public static FetchResponse fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new FetchResponse(wire.readEntries(), wire.readLong());
    }

    public Buffer toBuffer() {
        return Wire.appendEntries(Buffer.buffer(), entries).appendLong(nextOffset);
    }
}
