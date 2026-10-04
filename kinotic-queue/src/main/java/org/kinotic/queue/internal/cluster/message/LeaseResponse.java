package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

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
        Buffer ret = Wire.appendEntries(Buffer.buffer(), leased.stream().map(LeasedEntry::entry).toList());
        leased.forEach(entry -> ret.appendInt(entry.deliveryCount()));
        return ret;
    }
}
