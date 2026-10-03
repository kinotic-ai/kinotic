package org.kinotic.queue.internal.log;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads one shard's entries in offset order. A reader may move between threads, but only one thread may use it
 * at a time.
 */
public final class ShardReader implements AutoCloseable {

    private final ShardLog shardLog;
    private final ExcerptTailer tailer;
    private long nextOffset;

    ShardReader(ShardLog shardLog, ExcerptTailer tailer, long nextOffset) {
        this.shardLog = shardLog;
        this.tailer = tailer;
        this.nextOffset = nextOffset;
        if (nextOffset > 0) {
            // Positions after the previous entry, which exists even when nextOffset is the end of the shard
            tailer.moveToIndex(shardLog.indexOf(nextOffset - 1));
            try (DocumentContext ignored = tailer.readingDocument()) {
                // skips the previous entry
            }
        }
    }

    /**
     * Reads the entries written since the last read, stopping at {@code maxEntries} or once {@code maxBytes} is reached.
     * Always returns the next entry when there is one, however large.
     *
     * @return entries in offset order, empty when the reader has reached the end of the shard
     */
    public List<ShardEntry> read(int maxEntries, long maxBytes) {
        List<ShardEntry> ret = new ArrayList<>();
        long bytesRead = 0;
        tailer.singleThreadedCheckReset();
        while (ret.size() < maxEntries && bytesRead < maxBytes) {
            try (DocumentContext dc = tailer.readingDocument()) {
                if (!dc.isPresent()) {
                    break;
                }
                Bytes<?> bytes = dc.wire().bytes();
                long offset = bytes.readLong();
                // Guards the dense-offset invariant ShardLog.indexOf positions readers with
                if (offset != nextOffset) {
                    throw new IllegalStateException("Expected offset " + nextOffset + " in " + shardLog + " but read " + offset);
                }
                long epoch = bytes.readLong();
                ShardEntry entry;
                if (bytes.readBoolean()) {
                    entry = ShardEntry.marker(offset, epoch);
                } else {
                    String key = bytes.readUtf8();
                    byte[] payload = new byte[bytes.readInt()];
                    bytes.read(payload);
                    entry = new ShardEntry(offset, epoch, key, payload);
                }
                ret.add(entry);
                bytesRead += entry.size();
                nextOffset = offset + 1;
            }
        }
        return ret;
    }

    @Override
    public void close() {
        tailer.singleThreadedCheckReset();
        tailer.close();
    }
}
