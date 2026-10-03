package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;
import org.kinotic.queue.internal.log.ShardEntry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The binary encoding queue nodes exchange. The static methods append values to a buffer; an instance reads them
 * back in the same order.
 */
public final class Wire {

    private final Buffer buffer;
    private int position;

    public Wire(Buffer buffer) {
        this.buffer = buffer;
    }

    public static Buffer appendString(Buffer buffer, String value) {
        return appendBytes(buffer, value.getBytes(StandardCharsets.UTF_8));
    }

    public static Buffer appendBytes(Buffer buffer, byte[] value) {
        return buffer.appendInt(value.length).appendBytes(value);
    }

    public static Buffer appendEntries(Buffer buffer, List<ShardEntry> entries) {
        buffer.appendInt(entries.size());
        for (ShardEntry entry : entries) {
            buffer.appendLong(entry.offset()).appendLong(entry.epoch());
            appendString(buffer, entry.key());
            appendBytes(buffer, entry.payload());
        }
        return buffer;
    }

    public int readInt() {
        int ret = buffer.getInt(position);
        position += Integer.BYTES;
        return ret;
    }

    public long readLong() {
        long ret = buffer.getLong(position);
        position += Long.BYTES;
        return ret;
    }

    public String readString() {
        return new String(readBytes(), StandardCharsets.UTF_8);
    }

    public byte[] readBytes() {
        int length = readInt();
        byte[] ret = buffer.getBytes(position, position + length);
        position += length;
        return ret;
    }

    public List<ShardEntry> readEntries() {
        int count = readInt();
        List<ShardEntry> ret = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ret.add(new ShardEntry(readLong(), readLong(), readString(), readBytes()));
        }
        return ret;
    }
}
