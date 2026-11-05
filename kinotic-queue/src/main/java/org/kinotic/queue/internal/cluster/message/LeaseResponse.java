package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;
import org.kinotic.queue.internal.log.ShardEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * The records leased for a {@link LeaseRequest}, possibly none.
 */
public record LeaseResponse(List<LeasedEntry> leased) {

    public static LeaseResponse fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        var entries = wire.readEntries();
        List<LeasedEntry> leased = new ArrayList<>(entries.size());
        for (var entry : entries) {
            leased.add(new LeasedEntry(entry, wire.readInt()));
        }
        return new LeaseResponse(leased);
    }

    public Buffer toBuffer() {
        List<ShardEntry> entries = leased.stream().map(LeasedEntry::entry).toList();
        Buffer ret = Wire.appendEntries(Wire.buffer(entries), entries);
        leased.forEach(entry -> ret.appendInt(entry.deliveryCount()));
        return ret;
    }
}
