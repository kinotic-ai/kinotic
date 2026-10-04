package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;
import org.kinotic.queue.internal.log.ShardEntry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The binary encoding queue nodes exchange. The static methods append values to a buffer; an instance reads them
 * back in the same order.
 */
public final class Wire {

    // Raised whenever the layout of any message changes, so nodes running different layouts refuse each other's
    // messages instead of misreading them
    private static final byte VERSION = 1;

    private final Buffer buffer;
    private int position;

    /**
     * Reads a message written to a {@link #buffer()}.
     *
     * @throws IllegalArgumentException when the message was written in another version of the encoding
     */
    public Wire(Buffer buffer) {
        this.buffer = buffer;
        byte version = buffer.length() > 0 ? buffer.getByte(0) : -1;
        if (version != VERSION) {
            throw new IllegalArgumentException("Received a queue message of encoding version " + version + ", but this node uses version "
                                                       + VERSION + "; every queue node must run the same version");
        }
        this.position = Byte.BYTES;
    }

    /**
     * @return an empty message, to append values to with the static methods
     */
    public static Buffer buffer() {
        return Buffer.buffer().appendByte(VERSION);
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
            buffer.appendLong(entry.offset()).appendLong(entry.epoch()).appendByte((byte) (entry.isMarker() ? 1 : 0));
            if (!entry.isMarker()) {
                appendString(buffer, entry.key());
                appendBytes(buffer, entry.payload());
            }
        }
        return buffer;
    }

    public static Buffer appendOffsets(Buffer buffer, Map<String, Long> offsets) {
        buffer.appendInt(offsets.size());
        offsets.forEach((name, offset) -> appendString(buffer, name).appendLong(offset));
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

    public byte readByte() {
        byte ret = buffer.getByte(position);
        position += Byte.BYTES;
        return ret;
    }

    public List<ShardEntry> readEntries() {
        int count = readInt();
        List<ShardEntry> ret = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long offset = readLong();
            long epoch = readLong();
            ret.add(readByte() == 1 ? ShardEntry.marker(offset, epoch) : new ShardEntry(offset, epoch, readString(), readBytes()));
        }
        return ret;
    }

    public Map<String, Long> readOffsets() {
        int count = readInt();
        Map<String, Long> ret = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            ret.put(readString(), readLong());
        }
        return ret;
    }
}
