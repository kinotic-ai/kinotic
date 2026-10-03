package org.kinotic.queue.internal.log;

import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycle;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.DocumentContext;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * One shard of a queue on this node: an append-only log whose entries carry offsets that start at zero and
 * increase by one per entry. Writes are serialized; any number of {@link ShardReader}s may read concurrently.
 */
public final class ShardLog implements AutoCloseable {

    private final Path directory;
    // Chronicle sequence numbers are dense within a roll cycle and so are offsets, so an offset's queue index is
    // its cycle plus its distance from the first offset written in that cycle. Keyed by that first offset.
    private final ConcurrentSkipListMap<Long, Integer> cycleByFirstOffset = new ConcurrentSkipListMap<>();
    private SingleChronicleQueue queue;
    private RollCycle rollCycle;
    private ExcerptAppender appender;
    private volatile long nextOffset;
    private volatile long lastEpoch;
    private long acceptedEpoch;

    public ShardLog(Path directory) {
        this.directory = directory;
        open();
    }

    /**
     * Appends an entry as the shard's owner.
     *
     * @param epoch the owner's epoch
     * @return the offset the entry was written at
     * @throws StaleEpochException when the shard has accepted a newer owner
     */
    public synchronized long append(long epoch, String key, byte[] payload) {
        if (epoch < acceptedEpoch) {
            throw new StaleEpochException(epoch, acceptedEpoch);
        }
        acceptedEpoch = epoch;
        long ret = nextOffset;
        write(new ShardEntry(ret, epoch, key, payload));
        return ret;
    }

    /**
     * Applies a batch of entries from the shard's owner. The batch continues the shard when the shard holds the
     * entry before it with the same epoch the owner has; entries the shard already holds are skipped. A shard that
     * holds an entry the owner does not is emptied, so the owner can send it everything again.
     *
     * @param epoch           the owner's epoch
     * @param prevOffset      the offset before the batch's first entry, or -1 when the batch starts the shard
     * @param prevEpoch       the epoch of the entry at {@code prevOffset} on the owner
     * @param ownerNextOffset the offset after the owner's last entry
     * @param entries         consecutive entries starting at {@code prevOffset + 1}
     * @return how the batch was handled; when accepted, the offset after the batch
     */
    public synchronized ReplicationResult replicate(long epoch,
                                                    long prevOffset,
                                                    long prevEpoch,
                                                    long ownerNextOffset,
                                                    List<ShardEntry> entries) {
        ReplicationResult ret;
        if (epoch < acceptedEpoch) {
            ret = new ReplicationResult(ReplicationStatus.STALE_EPOCH, nextOffset);
        } else {
            acceptedEpoch = epoch;
            if (prevOffset >= nextOffset) {
                ret = new ReplicationResult(ReplicationStatus.MISMATCH, nextOffset);
            } else if (nextOffset > ownerNextOffset
                    || (prevOffset >= 0 && epochAt(prevOffset) != prevEpoch)
                    || !matchesHeldEntries(entries)) {
                reset();
                ret = new ReplicationResult(ReplicationStatus.MISMATCH, 0);
            } else {
                for (ShardEntry entry : entries) {
                    if (entry.offset() == nextOffset) {
                        write(entry);
                    }
                }
                // Entries past the batch are not yet compared with the owner's, so they are not reported as held
                ret = new ReplicationResult(ReplicationStatus.ACCEPTED, prevOffset + 1 + entries.size());
            }
        }
        return ret;
    }

    /**
     * Opens a reader whose first entry is the one at {@code offset}.
     *
     * @param offset at most {@link #nextOffset()}; a reader at {@link #nextOffset()} returns entries as they are written
     */
    public synchronized ShardReader newReader(long offset) {
        if (offset < 0 || offset > nextOffset) {
            throw new IllegalArgumentException("Offset " + offset + " is outside " + directory + ", which ends at " + nextOffset);
        }
        return new ShardReader(this, queue.createTailer(), offset);
    }

    /**
     * Reads up to {@code max} entries starting at {@code offset}.
     */
    public List<ShardEntry> read(long offset, int max) {
        try (ShardReader reader = newReader(offset)) {
            return reader.read(max);
        }
    }

    /**
     * @return the offset the next entry receives
     */
    public long nextOffset() {
        return nextOffset;
    }

    /**
     * @return the epoch of the last entry, or -1 when the shard is empty
     */
    public long lastEpoch() {
        return lastEpoch;
    }

    /**
     * @return the newest owner epoch the shard has accepted entries or batches from
     */
    public synchronized long acceptedEpoch() {
        return acceptedEpoch;
    }

    @Override
    public synchronized void close() {
        appender.singleThreadedCheckReset();
        appender.close();
        queue.close();
    }

    @Override
    public String toString() {
        return directory.toString();
    }

    long indexOf(long offset) {
        Map.Entry<Long, Integer> cycle = cycleByFirstOffset.floorEntry(offset);
        return rollCycle.toIndex(cycle.getValue(), offset - cycle.getKey());
    }

    private boolean matchesHeldEntries(List<ShardEntry> entries) {
        boolean ret = true;
        for (ShardEntry entry : entries) {
            if (entry.offset() < nextOffset && epochAt(entry.offset()) != entry.epoch()) {
                ret = false;
                break;
            }
        }
        return ret;
    }

    private long epochAt(long offset) {
        return read(offset, 1).getFirst().epoch();
    }

    private void write(ShardEntry entry) {
        appender.singleThreadedCheckReset();
        try (DocumentContext dc = appender.writingDocument()) {
            try {
                dc.wire().bytes()
                  .writeLong(entry.offset())
                  .writeLong(entry.epoch())
                  .writeUtf8(entry.key())
                  .writeInt(entry.payload().length)
                  .write(entry.payload());
            } catch (RuntimeException e) {
                // A partly written excerpt would take a sequence number without an offset, breaking indexOf
                dc.rollbackOnClose();
                throw e;
            }
        }
        long index = appender.lastIndexAppended();
        cycleByFirstOffset.putIfAbsent(entry.offset() - rollCycle.toSequenceNumber(index), rollCycle.toCycle(index));
        lastEpoch = entry.epoch();
        // Written after the cycle map so a reader that sees the new offset also finds its cycle
        nextOffset = entry.offset() + 1;
    }

    private void reset() {
        close();
        try {
            FileUtils.deleteDirectory(directory.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        open();
    }

    private void open() {
        queue = SingleChronicleQueueBuilder.binary(directory)
                                           .rollCycle(RollCycles.FAST_DAILY)
                                           .build();
        rollCycle = queue.rollCycle();
        appender = queue.createAppender();
        cycleByFirstOffset.clear();
        nextOffset = 0;
        lastEpoch = -1;
        if (queue.lastIndex() >= 0) {
            loadCycleOffsets();
        }
        acceptedEpoch = Math.max(acceptedEpoch, lastEpoch);
    }

    // Fills the cycle map from the first entry of every cycle on disk, and reads the last entry
    private void loadCycleOffsets() {
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
                nextOffset = dc.wire().bytes().readLong() + 1;
                lastEpoch = dc.wire().bytes().readLong();
            }
        }
    }
}
