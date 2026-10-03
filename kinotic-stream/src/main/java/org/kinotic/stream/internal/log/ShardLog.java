package org.kinotic.stream.internal.log;

import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycle;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.DocumentContext;
import org.kinotic.stream.api.model.StreamPosition;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One shard of a stream: an append-only log whose records carry offsets that start at zero and increase by
 * one per record. Appends are serialized; any number of {@link ShardReader}s may read concurrently.
 */
public final class ShardLog implements AutoCloseable {

    private final String stream;
    private final int shard;
    private final SingleChronicleQueue queue;
    private final RollCycle rollCycle;
    private final ExcerptAppender appender;
    // Chronicle sequence numbers are dense within a roll cycle and so are offsets, so an offset's queue index is
    // its cycle plus its distance from the first offset written in that cycle. Keyed by that first offset.
    private final ConcurrentSkipListMap<Long, Integer> cycleByFirstOffset = new ConcurrentSkipListMap<>();
    private final List<Runnable> appendListeners = new CopyOnWriteArrayList<>();
    private volatile long nextOffset;

    public ShardLog(String stream, int shard, Path directory) {
        this.stream = stream;
        this.shard = shard;
        this.queue = SingleChronicleQueueBuilder.binary(directory)
                                                .rollCycle(RollCycles.FAST_DAILY)
                                                .build();
        this.rollCycle = queue.rollCycle();
        this.appender = queue.createAppender();
        this.nextOffset = loadCycleOffsets();
    }

    /**
     * Appends a record and notifies the append listeners.
     *
     * @return the position the record was written at
     */
    public synchronized StreamPosition append(String key, byte[] payload) {
        long offset = nextOffset;
        appender.singleThreadedCheckReset();
        try (DocumentContext dc = appender.writingDocument()) {
            try {
                dc.wire().bytes()
                  .writeLong(offset)
                  .writeUtf8(key)
                  .writeInt(payload.length)
                  .write(payload);
            } catch (RuntimeException e) {
                // A partly written excerpt would take a sequence number without an offset, breaking indexOf
                dc.rollbackOnClose();
                throw e;
            }
        }
        long index = appender.lastIndexAppended();
        cycleByFirstOffset.putIfAbsent(offset - rollCycle.toSequenceNumber(index), rollCycle.toCycle(index));
        // Written after the cycle map so a reader that sees the new offset also finds its cycle
        nextOffset = offset + 1;
        appendListeners.forEach(Runnable::run);
        return new StreamPosition(stream, shard, offset);
    }

    /**
     * Opens a reader whose first record is the one at {@code offset}.
     *
     * @param offset at most {@link #nextOffset()}; a reader at {@link #nextOffset()} waits for the next append
     */
    public ShardReader newReader(long offset) {
        if (offset < 0 || offset > nextOffset) {
            throw new IllegalArgumentException("Offset " + offset + " is outside shard " + shard + " of stream "
                                                       + stream + ", which ends at " + nextOffset);
        }
        return new ShardReader(this, queue.createTailer(), offset);
    }

    /**
     * Registers a callback run on the appending thread after every append.
     */
    public void addAppendListener(Runnable listener) {
        appendListeners.add(listener);
    }

    public void removeAppendListener(Runnable listener) {
        appendListeners.remove(listener);
    }

    /**
     * @return the offset the next appended record receives
     */
    public long nextOffset() {
        return nextOffset;
    }

    public String stream() {
        return stream;
    }

    public int shard() {
        return shard;
    }

    @Override
    public synchronized void close() {
        appender.singleThreadedCheckReset();
        appender.close();
        queue.close();
    }

    long indexOf(long offset) {
        Map.Entry<Long, Integer> cycle = cycleByFirstOffset.floorEntry(offset);
        return rollCycle.toIndex(cycle.getValue(), offset - cycle.getKey());
    }

    /**
     * Fills the cycle map from the first record of every cycle on disk.
     *
     * @return the offset after the last record on disk
     */
    private long loadCycleOffsets() {
        long ret = 0;
        if (queue.lastIndex() >= 0) {
            try (ExcerptTailer tailer = queue.createTailer()) {
                for (Long cycle : queue.listCyclesBetween(queue.firstCycle(), queue.lastCycle())) {
                    if (tailer.moveToCycle(cycle.intValue())) {
                        try (DocumentContext dc = tailer.readingDocument()) {
                            if (dc.isPresent()) {
                                long offset = dc.wire().bytes().readLong();
                                cycleByFirstOffset.put(offset - rollCycle.toSequenceNumber(dc.index()), cycle.intValue());
                            }
                        }
                    }
                }
                tailer.moveToIndex(queue.lastIndex());
                try (DocumentContext dc = tailer.readingDocument()) {
                    ret = dc.wire().bytes().readLong() + 1;
                }
            }
        }
        return ret;
    }
}
