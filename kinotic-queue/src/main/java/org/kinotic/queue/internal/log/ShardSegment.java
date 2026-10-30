package org.kinotic.queue.internal.log;

import lombok.Getter;
import lombok.experimental.Accessors;
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
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

/**
 * A contiguous run of one shard's entries, stored in one Chronicle Queue: the entry at {@link #startOffset()} is
 * written first and every later entry has the next offset. A sealed segment ends at the offset it was sealed at;
 * entries written past that offset are never read. Writes are serialized by the {@link ShardLog}; reads may run
 * concurrently with writes.
 */
@Accessors(fluent = true)
final class ShardSegment implements AutoCloseable {

    private final Path directory;
    @Getter
    private final long startOffset;
    /**
     * When the segment was created, in epoch milliseconds; every entry of the segment before it was written after this.
     */
    @Getter
    private final long createdMillis;
    private final SingleChronicleQueue queue;
    private final RollCycle rollCycle;
    // The queue's clock stays at the segment's creation, so all of the segment is one cycle file, in which Chronicle
    // sequence numbers are dense like offsets: an offset's queue index is its distance from the segment's start
    private final int cycle;
    private final ExcerptAppender appender;
    // Tailers no read is using. Creating a tailer refreshes the queue's directory listing, which costs far more than a
    // read, so reads take one from here and put it back. The ShardLog closes a segment only while no read uses it.
    private final ConcurrentLinkedDeque<ExcerptTailer> idleTailers = new ConcurrentLinkedDeque<>();
    // The segment's cycle file, once an entry was written to it since the segment opened
    private File cycleFile;
    // The offset before which every entry is forced to disk
    private long syncedEnd;
    private volatile long nextOffset;
    private volatile long sealedEnd;
    /**
     * About how many bytes the segment's entries take: the {@link ShardEntry#size sizes} of the entries written to it,
     * counting ones past its sealed end.
     */
    @Getter
    private volatile long bytes;

    /**
     * Opens the segment stored in {@code directory}, creating it empty when there is none.
     *
     * @param sealedEnd     the offset the segment was sealed at, or -1 when it is open for writes; a sealed segment
     *                      ends at the end of the entries on disk when they end before it
     * @param createdMillis when the segment was created, in epoch milliseconds
     * @param bytes         the size of the entries written to the segment so far, as last recorded
     */
    ShardSegment(Path directory, long startOffset, long sealedEnd, long createdMillis, long bytes) {
        this.directory = directory;
        this.startOffset = startOffset;
        this.createdMillis = createdMillis;
        this.bytes = bytes;
        this.queue = SingleChronicleQueueBuilder.binary(directory)
                                                .rollCycle(RollCycles.FAST_DAILY)
                                                .timeProvider(() -> createdMillis)
                                                .build();
        this.rollCycle = queue.rollCycle();
        this.cycle = queue.cycle();
        long lastIndex = queue.lastIndex();
        this.nextOffset = lastIndex >= 0 ? startOffset + rollCycle.toSequenceNumber(lastIndex) + 1 : startOffset;
        // Entries a crash lost before reaching the disk may have been sealed in the manifest, which is always forced
        this.sealedEnd = sealedEnd >= 0 ? Math.min(sealedEnd, nextOffset) : -1;
        this.syncedEnd = nextOffset;
        this.appender = queue.createAppender();
    }

    /**
     * @return the name of the segment's directory, unique among the shard's segments
     */
    String name() {
        return directory.getFileName().toString();
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
                     .writeByte(entry.type().code());
                switch (entry.type()) {
                    case RECORD -> bytes.writeUtf8(entry.key())
                                        .writeInt(entry.payload().length)
                                        .write(entry.payload())
                                        .writeLong(entry.slot().producerId())
                                        .writeLong(entry.slot().sequence())
                                        .writeInt(entry.slot().index())
                                        .writeInt(entry.slot().size());
                    case MEMBERSHIP -> {
                        writeStorageIds(bytes, entry.membership().voting());
                        writeStorageIds(bytes, entry.membership().joining());
                    }
                    case MARKER -> {
                    }
                }
            } catch (RuntimeException e) {
                // A partly written excerpt would take a sequence number without an offset, breaking indexOf
                dc.rollbackOnClose();
                throw e;
            }
        }
        cycleFile = appender.currentFile();
        this.bytes += entry.size();
        nextOffset = entry.offset() + 1;
    }

    /**
     * Forces every entry written so far to disk.
     */
    void sync() {
        if (syncedEnd < nextOffset) {
            DurableFiles.force(cycleFile.toPath());
            // The first entries also created the cycle file, the queue's metadata files and the directory itself
            if (syncedEnd == startOffset) {
                try (Stream<Path> files = Files.list(directory)) {
                    files.filter(file -> file.getFileName().toString().endsWith(".cq4t")).forEach(DurableFiles::force);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                DurableFiles.force(directory);
                DurableFiles.force(directory.getParent());
            }
            syncedEnd = nextOffset;
        }
    }

    /**
     * Reads entries starting at {@code offset}, stopping at the segment's end, at {@code maxEntries}, or once the sizes
     * of the entries read reach {@code maxBytes}. Always returns the entry at {@code offset} when the segment holds it,
     * however large.
     *
     * @param decoder decodes what is read of an entry from its fields after its offset, given that offset:
     *                {@link #decodeEntry}, {@link #decodeEpoch} or {@link #decodeSlot}
     * @param size    the size an entry read counts toward {@code maxBytes}
     */
    <T> List<T> read(long offset, int maxEntries, long maxBytes, BiFunction<Long, Bytes<?>, T> decoder, ToLongFunction<T> size) {
        List<T> ret = new ArrayList<>();
        long end = end();
        if (offset >= startOffset && offset < end) {
            ExcerptTailer tailer = idleTailers.poll();
            if (tailer == null) {
                tailer = queue.createTailer();
            }
            // The tailer may last have read on another thread
            tailer.singleThreadedCheckReset();
            try {
                tailer.moveToIndex(indexOf(offset));
                long expected = offset;
                long bytesRead = 0;
                while (expected < end && ret.size() < maxEntries && bytesRead < maxBytes) {
                    T entry = readNext(tailer, expected, decoder);
                    ret.add(entry);
                    bytesRead += size.applyAsLong(entry);
                    expected++;
                }
            } finally {
                idleTailers.push(tailer);
            }
        }
        return ret;
    }

    /**
     * Decodes an entry in full.
     */
    static ShardEntry decodeEntry(long offset, Bytes<?> bytes) {
        long epoch = bytes.readLong();
        return switch (EntryType.fromCode(bytes.readByte())) {
            case RECORD -> {
                String key = bytes.readUtf8();
                byte[] payload = new byte[bytes.readInt()];
                bytes.read(payload);
                yield ShardEntry.record(offset, epoch, key, payload, readSlot(bytes));
            }
            case MEMBERSHIP -> ShardEntry.membership(offset, epoch, new Membership(readStorageIds(bytes), readStorageIds(bytes)));
            case MARKER -> ShardEntry.marker(offset, epoch);
        };
    }

    /**
     * Decodes an entry's epoch, and nothing else of it.
     */
    static long decodeEpoch(long offset, Bytes<?> bytes) {
        return bytes.readLong();
    }

    /**
     * Decodes where a record sat in the batch it was appended in, and the record's size, without copying its payload.
     */
    static RecordSlot decodeSlot(long offset, Bytes<?> bytes) {
        bytes.readSkip(Long.BYTES);
        RecordSlot ret;
        if (EntryType.fromCode(bytes.readByte()) == EntryType.RECORD) {
            String key = bytes.readUtf8();
            int payloadLength = bytes.readInt();
            bytes.readSkip(payloadLength);
            ret = new RecordSlot(offset, readSlot(bytes), ShardEntry.size(key, payloadLength));
        } else {
            ret = new RecordSlot(offset, null, 0);
        }
        return ret;
    }

    @Override
    public void close() {
        for (ExcerptTailer tailer : idleTailers) {
            tailer.singleThreadedCheckReset();
            tailer.close();
        }
        idleTailers.clear();
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
        return rollCycle.toIndex(cycle, offset - startOffset);
    }

    private <T> T readNext(ExcerptTailer tailer, long expected, BiFunction<Long, Bytes<?>, T> decoder) {
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
            return decoder.apply(offset, bytes);
        }
    }

    private static BatchSlot readSlot(Bytes<?> bytes) {
        return new BatchSlot(bytes.readLong(), bytes.readLong(), bytes.readInt(), bytes.readInt());
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
}
