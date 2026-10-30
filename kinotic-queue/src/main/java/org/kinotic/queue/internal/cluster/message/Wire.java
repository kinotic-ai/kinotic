package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;
import org.kinotic.queue.internal.log.BatchSlot;
import org.kinotic.queue.internal.log.ConsumerPosition;
import org.kinotic.queue.internal.log.EntryType;
import org.kinotic.queue.internal.log.Membership;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardPositions;

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
    private static final byte VERSION = 8;

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

    // About the bytes an entry takes besides its key and payload: offset, epoch, type, lengths and batch slot
    private static final int ENTRY_OVERHEAD_BYTES = 64;

    /**
     * @return an empty message, to append values to with the static methods
     */
    public static Buffer buffer() {
        return Buffer.buffer().appendByte(VERSION);
    }

    /**
     * @return an empty message with room for about {@code expectedBytes}, so appending them does not grow it over and
     * over
     */
    public static Buffer buffer(int expectedBytes) {
        return Buffer.buffer(Byte.BYTES + expectedBytes).appendByte(VERSION);
    }

    /**
     * @return an empty message with room for the entries
     */
    public static Buffer buffer(List<ShardEntry> entries) {
        return buffer(entries.stream().mapToInt(entry -> entry.size() + ENTRY_OVERHEAD_BYTES).sum());
    }

    public static Buffer appendString(Buffer buffer, String value) {
        return appendBytes(buffer, value.getBytes(StandardCharsets.UTF_8));
    }

    public static Buffer appendBytes(Buffer buffer, byte[] value) {
        return buffer.appendInt(value.length).appendBytes(value);
    }

    public static Buffer appendShardId(Buffer buffer, ShardId shardId) {
        return appendString(appendString(buffer, shardId.queue()), shardId.incarnation()).appendInt(shardId.shard());
    }

    public static Buffer appendEntries(Buffer buffer, List<ShardEntry> entries) {
        buffer.appendInt(entries.size());
        for (ShardEntry entry : entries) {
            buffer.appendLong(entry.offset()).appendLong(entry.epoch()).appendByte(entry.type().code());
            switch (entry.type()) {
                case RECORD -> appendSlot(appendBytes(appendString(buffer, entry.key()), entry.payload()), entry.slot());
                case MEMBERSHIP -> appendStrings(appendStrings(buffer, entry.membership().voting()), entry.membership().joining());
                case MARKER -> {
                }
            }
        }
        return buffer;
    }

    public static Buffer appendPositions(Buffer buffer, ShardPositions positions) {
        return appendPositionMap(appendPositionMap(buffer, positions.consumers()), positions.groups());
    }

    private static Buffer appendStrings(Buffer buffer, List<String> values) {
        buffer.appendInt(values.size());
        values.forEach(value -> appendString(buffer, value));
        return buffer;
    }

    private static Buffer appendSlot(Buffer buffer, BatchSlot slot) {
        return buffer.appendLong(slot.producerId()).appendLong(slot.sequence()).appendInt(slot.index()).appendInt(slot.size());
    }

    private static Buffer appendPositionMap(Buffer buffer, Map<String, ConsumerPosition> positions) {
        buffer.appendInt(positions.size());
        positions.forEach((name, position) -> appendString(buffer, name).appendLong(position.nextOffset()).appendLong(position.lastUsedMillis()));
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

    public ShardId readShardId() {
        return new ShardId(readString(), readString(), readInt());
    }

    public List<ShardEntry> readEntries() {
        int count = readInt();
        List<ShardEntry> ret = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long offset = readLong();
            long epoch = readLong();
            ret.add(switch (EntryType.fromCode(readByte())) {
                case RECORD -> ShardEntry.record(offset, epoch, readString(), readBytes(), readSlot());
                case MEMBERSHIP -> ShardEntry.membership(offset, epoch, new Membership(readStrings(), readStrings()));
                case MARKER -> ShardEntry.marker(offset, epoch);
            });
        }
        return ret;
    }

    public ShardPositions readPositions() {
        return new ShardPositions(readPositionMap(), readPositionMap());
    }

    private List<String> readStrings() {
        int count = readInt();
        List<String> ret = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ret.add(readString());
        }
        return List.copyOf(ret);
    }

    private BatchSlot readSlot() {
        return new BatchSlot(readLong(), readLong(), readInt(), readInt());
    }

    private Map<String, ConsumerPosition> readPositionMap() {
        int count = readInt();
        Map<String, ConsumerPosition> ret = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            ret.put(readString(), new ConsumerPosition(readLong(), readLong()));
        }
        return ret;
    }
}
