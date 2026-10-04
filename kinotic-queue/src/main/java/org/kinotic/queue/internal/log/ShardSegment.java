package org.kinotic.queue.internal.log;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycle;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.DocumentContext;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

/**
 * A contiguous run of one shard's entries, stored in one Chronicle Queue: the entry at {@link #startOffset()} is
 * written first and every later entry has the next offset. A sealed segment ends at the offset it was sealed at;
 * entries written past that offset are never read. Writes are serialized by the {@link ShardLog}; reads may run
 * concurrently with writes.
 */
final class ShardSegment implements AutoCloseable {

    private final Path directory;
    private final long startOffset;
    // Chronicle sequence numbers are dense within a roll cycle and so are offsets, so an offset's queue index is
    // its cycle plus its distance from the first offset written in that cycle. Keyed by that first offset.
    private final ConcurrentSkipListMap<Long, Integer> cycleByFirstOffset = new ConcurrentSkipListMap<>();
    private final SingleChronicleQueue queue;
    private final RollCycle rollCycle;
    private final ExcerptAppender appender;
    // The cycle file the last write went to, forced to disk by sync()
    private File writtenFile;
    private boolean newCycleWritten;
    private volatile long nextOffset;
    private volatile long sealedEnd = -1;

    private ShardSegment(Path directory, long startOffset) {
        this.directory = directory;
        this.startOffset = startOffset;
        this.queue = SingleChronicleQueueBuilder.binary(directory)
                                                .rollCycle(RollCycles.FAST_DAILY)
                                                .build();
        this.rollCycle = queue.rollCycle();
        this.appender = queue.createAppender();
        this.nextOffset = startOffset;
        if (queue.lastIndex() >= 0) {
            loadCycleOffsets();
        }
    }

    /**
     * Opens the segment stored in {@code directory}, creating it empty when there is none.
     *
     * @param sealedEnd the offset the segment was sealed at, or -1 when it is open for writes
     */
    static ShardSegment open(Path directory, long startOffset, long sealedEnd) {
        ShardSegment ret = new ShardSegment(directory, startOffset);
        ret.sealedEnd = sealedEnd;
        return ret;
    }

    /**
     * @return the name of the segment's directory, unique among the shard's segments
     */
    String name() {
        return directory.getFileName().toString();
    }

    long startOffset() {
        return startOffset;
    }

    /**
     * @return the offset after the last entry that may be read: the sealed end, or the offset the next write receives
     */
    long end() {
        long sealed = sealedEnd;
        return sealed >= 0 ? sealed : nextOffset;
    }

    boolean isSealed() {
        return sealedEnd >= 0;
    }

    /**
     * Ends the segment at {@code offset}, which is at least its start and at most its end.
     */
    void seal(long offset) {
        sealedEnd = offset;
    }

    /**
     * Writes the entry, which must have the offset {@link #end()} of an open segment.
     */
    void write(ShardEntry entry) {
        appender.singleThreadedCheckReset();
        try (DocumentContext dc = appender.writingDocument()) {
            try {
                Bytes<?> bytes = dc.wire().bytes();
                bytes.writeLong(entry.offset())
                     .writeLong(entry.epoch())
                     .writeBoolean(entry.isMarker());
                if (!entry.isMarker()) {
                    bytes.writeUtf8(entry.key())
                         .writeInt(entry.payload().length)
                         .write(entry.payload());
                }
            } catch (RuntimeException e) {
                // A partly written excerpt would take a sequence number without an offset, breaking indexOf
                dc.rollbackOnClose();
                throw e;
            }
        }
        long index = appender.lastIndexAppended();
        if (cycleByFirstOffset.putIfAbsent(entry.offset() - rollCycle.toSequenceNumber(index), rollCycle.toCycle(index)) == null) {
            newCycleWritten = true;
        }
        writtenFile = appender.currentFile();
        // Written after the cycle map so a reader that sees the new offset also finds its cycle
        nextOffset = entry.offset() + 1;
    }

    /**
     * Forces every entry written so far to disk.
     */
    void sync() {
        if (writtenFile != null) {
            ShardLog.force(writtenFile.toPath());
            if (newCycleWritten) {
                // A new cycle adds a file to the directory and updates the queue's metadata files
                try (Stream<Path> files = Files.list(directory)) {
                    files.filter(file -> file.getFileName().toString().endsWith(".cq4t")).forEach(ShardLog::force);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                ShardLog.force(directory);
                newCycleWritten = false;
            }
        }
    }

    /**
     * Reads entries starting at {@code offset}, stopping at the segment's end, at {@code maxEntries}, or once
     * {@code maxBytes} is reached. Always returns the entry at {@code offset} when the segment holds it, however large.
     */
    List<ShardEntry> read(long offset, int maxEntries, long maxBytes) {
        List<ShardEntry> ret = new ArrayList<>();
        long end = end();
        if (offset >= startOffset && offset < end) {
            try (ExcerptTailer tailer = queue.createTailer()) {
                tailer.moveToIndex(indexOf(offset));
                long expected = offset;
                long bytesRead = 0;
                while (expected < end && ret.size() < maxEntries && bytesRead < maxBytes) {
                    ShardEntry entry = readNext(tailer, expected);
                    ret.add(entry);
                    bytesRead += entry.size();
                    expected++;
                }
            }
        }
        return ret;
    }

    @Override
    public void close() {
        appender.singleThreadedCheckReset();
        appender.close();
        queue.close();
    }

    /**
     * Closes the segment and removes it from disk.
     */
    void delete() {
        close();
        try {
            FileUtils.deleteDirectory(directory.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private long indexOf(long offset) {
        Map.Entry<Long, Integer> cycle = cycleByFirstOffset.floorEntry(offset);
        return rollCycle.toIndex(cycle.getValue(), offset - cycle.getKey());
    }

    private ShardEntry readNext(ExcerptTailer tailer, long expected) {
        try (DocumentContext dc = tailer.readingDocument()) {
            if (!dc.isPresent()) {
                throw new IllegalStateException("Offset " + expected + " is missing from " + directory);
            }
            Bytes<?> bytes = dc.wire().bytes();
            long offset = bytes.readLong();
            // Guards the dense-offset invariant indexOf positions the tailer with
            if (offset != expected) {
                throw new IllegalStateException("Expected offset " + expected + " in " + directory + " but read " + offset);
            }
            long epoch = bytes.readLong();
            ShardEntry ret;
            if (bytes.readBoolean()) {
                ret = ShardEntry.marker(offset, epoch);
            } else {
                String key = bytes.readUtf8();
                byte[] payload = new byte[bytes.readInt()];
                bytes.read(payload);
                ret = new ShardEntry(offset, epoch, key, payload);
            }
            return ret;
        }
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
            }
        }
    }
}
