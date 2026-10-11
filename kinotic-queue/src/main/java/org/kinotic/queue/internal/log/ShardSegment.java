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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * A contiguous run of one shard's entries, stored in one Chronicle Queue: the entry at {@link #startOffset()} is
 * written first and every later entry has the next offset. A sealed segment ends at the offset it was sealed at;
 * entries written past that offset are never read. Writes are serialized by the {@link ShardLog}; reads may run
 * concurrently with writes.
 */
final class ShardSegment implements AutoCloseable {

    private static final byte RECORD = 0;
    private static final byte MARKER = 1;
    private static final byte MEMBERSHIP = 2;

    private final Path directory;
    private final long startOffset;
    private final long createdMillis;
    // Chronicle sequence numbers are dense within a roll cycle and so are offsets, so an offset's queue index is
    // its cycle plus its distance from the first offset written in that cycle. Keyed by that first offset.
    private final ConcurrentSkipListMap<Long, Integer> cycleByFirstOffset = new ConcurrentSkipListMap<>();
    // The queue's clock stays at or after the start of its newest cycle file, so a wall clock set back across a day
    // keeps writing to that file: an entry written to an earlier cycle would sit past the end a tailer reads to
    private final AtomicLong latestTime = new AtomicLong();
    private final SingleChronicleQueue queue;
    private final RollCycle rollCycle;
    private final ExcerptAppender appender;
    // The cycle files written since the last sync(), which forces them to disk
    private final Set<File> unsyncedFiles = new LinkedHashSet<>();
    private boolean newCycleWritten;
    private volatile long nextOffset;
    private volatile long sealedEnd = -1;
    private volatile long bytes;

    private ShardSegment(Path directory, long startOffset, long createdMillis, long bytes) {
        this.directory = directory;
        this.startOffset = startOffset;
        this.createdMillis = createdMillis;
        this.bytes = bytes;
        this.queue = SingleChronicleQueueBuilder.binary(directory)
                                                .rollCycle(RollCycles.FAST_DAILY)
                                                .timeProvider(() -> latestTime.accumulateAndGet(System.currentTimeMillis(), Math::max))
                                                .build();
        this.rollCycle = queue.rollCycle();
        this.nextOffset = startOffset;
        if (queue.lastIndex() >= 0) {
            latestTime.set((long) queue.lastCycle() * rollCycle.lengthInMillis());
            loadCycleOffsets();
        }
        this.appender = queue.createAppender();
    }

    /**
     * Opens the segment stored in {@code directory}, creating it empty when there is none.
     *
     * @param sealedEnd     the offset the segment was sealed at, or -1 when it is open for writes; a sealed segment
     *                      ends at the end of the entries on disk when they end before it
     * @param createdMillis when the segment was created, in epoch milliseconds
     * @param bytes         the size of the entries written to the segment so far, as last recorded
     */
    static ShardSegment open(Path directory, long startOffset, long sealedEnd, long createdMillis, long bytes) {
        ShardSegment ret = new ShardSegment(directory, startOffset, createdMillis, bytes);
        // Entries a crash lost before reaching the disk may have been sealed in the manifest, which is always forced
        ret.sealedEnd = sealedEnd >= 0 ? Math.min(sealedEnd, ret.nextOffset) : -1;
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
     * @return when the segment was created, in epoch milliseconds; every entry of the segment before it was written
     * after this
     */
    long createdMillis() {
        return createdMillis;
    }

    /**
     * @return about how many bytes the segment's entries take: the {@link ShardEntry#size sizes} of the entries written
     * to it, counting ones past its sealed end
     */
    long bytes() {
        return bytes;
    }

    /**
     * Ends the segment at {@code offset}, which is at least its start and at most its end, or with -1 opens it to
     * writes again.
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
                     .writeByte(typeOf(entry));
                if (entry.isRecord()) {
                    bytes.writeUtf8(entry.key())
                         .writeInt(entry.payload().length)
                         .write(entry.payload())
                         .writeLong(entry.slot().producerId())
                         .writeLong(entry.slot().sequence())
                         .writeInt(entry.slot().index())
                         .writeInt(entry.slot().size());
                } else if (entry.isMembership()) {
                    writeStorageIds(bytes, entry.membership().voting());
                    writeStorageIds(bytes, entry.membership().joining());
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
        unsyncedFiles.add(appender.currentFile());
        this.bytes += entry.size();
        // Written after the cycle map so a reader that sees the new offset also finds its cycle
        nextOffset = entry.offset() + 1;
    }

    /**
     * Forces every entry written so far to disk.
     */
    void sync() {
        // The entries written since the last sync span two files when the cycle rolled between them
        for (File file : unsyncedFiles) {
            ShardLog.force(file.toPath());
        }
        unsyncedFiles.clear();
        if (newCycleWritten) {
            // A new cycle adds a file to the directory and updates the queue's metadata files; the first one also
            // follows the creation of the directory itself
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(file -> file.getFileName().toString().endsWith(".cq4t")).forEach(ShardLog::force);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            ShardLog.force(directory);
            ShardLog.force(directory.getParent());
            newCycleWritten = false;
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
            byte type = bytes.readByte();
            ShardEntry ret;
            if (type == RECORD) {
                String key = bytes.readUtf8();
                byte[] payload = new byte[bytes.readInt()];
                bytes.read(payload);
                BatchSlot slot = new BatchSlot(bytes.readLong(), bytes.readLong(), bytes.readInt(), bytes.readInt());
                ret = ShardEntry.record(offset, epoch, key, payload, slot);
            } else if (type == MEMBERSHIP) {
                ret = ShardEntry.membership(offset, epoch, new Membership(readStorageIds(bytes), readStorageIds(bytes)));
            } else {
                ret = ShardEntry.marker(offset, epoch);
            }
            return ret;
        }
    }

    private static byte typeOf(ShardEntry entry) {
        byte ret = MARKER;
        if (entry.isRecord()) {
            ret = RECORD;
        } else if (entry.isMembership()) {
            ret = MEMBERSHIP;
        }
        return ret;
    }

    private static void writeStorageIds(Bytes<?> bytes, List<String> storageIds) {
        bytes.writeInt(storageIds.size());
        storageIds.forEach(bytes::writeUtf8);
    }

    private static List<String> readStorageIds(Bytes<?> bytes) {
        int count = bytes.readInt();
        List<String> ret = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ret.add(bytes.readUtf8());
        }
        return List.copyOf(ret);
    }

    // Fills the cycle map from the first entry of every cycle on disk, and reads the last entry
    private void loadCycleOffsets() {
        try (ExcerptTailer tailer = queue.createTailer()) {
            for (Long cycle : queue.listCyclesBetween(queue.firstCycle(), queue.lastCycle())) {
                if (tailer.moveToCycle(cycle.intValue())) {
                    try (DocumentContext dc = tailer.readingDocument()) {
                        if (dc.isPresent()) {
                            long offset = dc.wire().bytes().readLong();
                            cycleByFirstOffset.put(offset - rollCycle.toSequenceNumber(dc.index()), rollCycle.toCycle(dc.index()));
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
