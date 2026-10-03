package org.kinotic.stream.internal.log;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;
import org.kinotic.stream.api.model.StreamPosition;
import org.kinotic.stream.api.model.StreamRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads one shard's records in offset order. A reader may move between threads, but only one thread may use it
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
            // Positions after the previous record, which exists even when nextOffset is the end of the shard
            tailer.moveToIndex(shardLog.indexOf(nextOffset - 1));
            try (DocumentContext ignored = tailer.readingDocument()) {
                // skips the previous record
            }
        }
    }

    /**
     * Reads the records appended since the last read.
     *
     * @param max the most records to return
     * @return up to {@code max} records in offset order, empty when the reader has reached the end of the shard
     */
    public List<StreamRecord> read(int max) {
        List<StreamRecord> ret = new ArrayList<>();
        tailer.singleThreadedCheckReset();
        while (ret.size() < max) {
            try (DocumentContext dc = tailer.readingDocument()) {
                if (!dc.isPresent()) {
                    break;
                }
                Bytes<?> bytes = dc.wire().bytes();
                long offset = bytes.readLong();
                // Guards the dense-offset invariant ShardLog.indexOf positions readers with
                if (offset != nextOffset) {
                    throw new IllegalStateException("Expected offset " + nextOffset + " in shard " + shardLog.shard()
                                                            + " of stream " + shardLog.stream() + " but read " + offset);
                }
                String key = bytes.readUtf8();
                byte[] payload = new byte[bytes.readInt()];
                bytes.read(payload);
                ret.add(new StreamRecord(new StreamPosition(shardLog.stream(), shardLog.shard(), offset), key, payload));
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
