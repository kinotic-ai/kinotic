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

    /**
     * @return the response a fetch from the same offset for at most {@code max} entries and {@code maxBytes} bytes
     * gets: as many of the entries as {@link org.kinotic.queue.internal.log.ShardLog#read} returns for those limits
     */
    public FetchResponse limit(int max, long maxBytes) {
        int count = 0;
        long bytes = 0;
        while (count < entries.size() && count < max && bytes < maxBytes) {
            bytes += entries.get(count).size();
            count++;
        }
        return count == entries.size() ? this : new FetchResponse(entries.subList(0, count), nextOffset - (entries.size() - count));
    }

    public static FetchResponse fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new FetchResponse(wire.readEntries(), wire.readLong());
    }

    public Buffer toBuffer() {
        return Wire.appendEntries(Wire.buffer(entries), entries).appendLong(nextOffset);
    }
}
