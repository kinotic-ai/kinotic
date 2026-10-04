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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * One shard of a queue on this node: an append-only log whose entries carry offsets that start at zero and
 * increase by one per entry, and epochs that never decrease from one entry to the next. Writes are serialized; any
 * number of {@link ShardReader}s may read concurrently.
 */
public final class ShardLog implements AutoCloseable {

    private static final String TRUNCATING_SUFFIX = ".truncating";
    private static final String REPLACED_SUFFIX = ".replaced";
    private static final String ACCEPTED_EPOCH_SUFFIX = ".accepted-epoch";
    private static final int COPY_BATCH_SIZE = 4_096;

    private final Path directory;
    // Chronicle sequence numbers are dense within a roll cycle and so are offsets, so an offset's queue index is
    // its cycle plus its distance from the first offset written in that cycle. Keyed by that first offset.
    private final ConcurrentSkipListMap<Long, Integer> cycleByFirstOffset = new ConcurrentSkipListMap<>();
    private SingleChronicleQueue queue;
    private RollCycle rollCycle;
    private ExcerptAppender appender;
    private volatile long nextOffset;
    private volatile long lastEpoch;
    private volatile long acceptedEpoch;

    public ShardLog(Path directory) {
        this.directory = directory;
        completeInterruptedTruncation();
        open();
        acceptedEpoch = Math.max(readAcceptedEpoch(), lastEpoch);
    }

    /**
     * @return whether a shard is stored in {@code directory}
     */
    public static boolean exists(Path directory) {
        return Files.isDirectory(directory) || Files.isDirectory(sibling(directory, TRUNCATING_SUFFIX));
    }

    /**
     * Appends a record as the shard's owner.
     *
     * @param epoch the owner's epoch
     * @return the offset the record was written at
     * @throws StaleEpochException when the shard has promised a newer owner
     */
    public synchronized long append(long epoch, String key, byte[] payload) {
        return appendEntry(epoch, key, payload);
    }

    /**
     * Appends the marker an owner writes first in its epoch.
     *
     * @return the offset the marker was written at
     * @throws StaleEpochException when the shard has promised a newer owner
     */
    public synchronized long appendMarker(long epoch) {
        return appendEntry(epoch, null, null);
    }

    /**
     * Promises not to accept entries from an owner older than {@code epoch}. The promise survives restarts.
     *
     * @return the newest epoch the shard had promised before this call, or -1 when it had promised none
     */
    public synchronized long promise(long epoch) {
        long ret = acceptedEpoch;
        if (epoch > acceptedEpoch) {
            writeAcceptedEpoch(epoch);
            acceptedEpoch = epoch;
        }
        return ret;
    }

    /**
     * Applies a batch of entries from the shard's owner. The batch continues the shard when the shard holds the entry
     * before it with the epoch the owner has there; entries with the same offset and epoch are the same entry, so the
     * shard then matches the owner up to that entry. Entries the shard already holds are skipped. From the first entry
     * that differs from the owner's, the shard's entries are replaced by the owner's; entries of older epochs the shard
     * holds past the owner's end are removed.
     *
     * @param epoch           the owner's epoch
     * @param prevOffset      the offset before the batch's first entry, or -1 when the batch starts the shard
     * @param prevEpoch       the epoch of the entry at {@code prevOffset} on the owner
     * @param ownerNextOffset the offset after the owner's last entry when the batch was read
     * @param entries         consecutive entries starting at {@code prevOffset + 1}
     * @return how the batch was handled
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
            promise(epoch);
            long heldPrevEpoch = prevOffset >= 0 && prevOffset < nextOffset ? epochAt(prevOffset) : prevEpoch;
            if (prevOffset >= nextOffset) {
                ret = new ReplicationResult(ReplicationStatus.MISMATCH, nextOffset);
            } else if (heldPrevEpoch != prevEpoch) {
                // Every entry of the differing epoch is suspect, so the owner resends from that epoch's first entry
                ret = new ReplicationResult(ReplicationStatus.MISMATCH, firstOffsetOfEpoch(heldPrevEpoch, prevOffset));
            } else {
                long batchEnd = prevOffset + 1 + entries.size();
                long conflict = firstConflict(prevOffset + 1, entries);
                if (conflict >= 0) {
                    truncate(conflict);
                }
                for (ShardEntry entry : entries) {
                    if (entry.offset() == nextOffset) {
                        write(entry);
                    }
                }
                // Entries of the owner's epoch past the batch came from the owner after it read the batch, which a
                // delayed batch arriving after a later one finds; only entries of older epochs there are stale
                if (batchEnd == ownerNextOffset && nextOffset > ownerNextOffset && lastEpoch < epoch) {
                    truncate(ownerNextOffset);
                }
                ret = new ReplicationResult(ReplicationStatus.ACCEPTED, batchEnd);
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
     * Reads entries starting at {@code offset}, stopping at {@code maxEntries} or once {@code maxBytes} is reached.
     * Always returns the entry at {@code offset} when there is one, however large.
     */
    public List<ShardEntry> read(long offset, int maxEntries, long maxBytes) {
        try (ShardReader reader = newReader(offset)) {
            return reader.read(maxEntries, maxBytes);
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
     * @return the newest epoch the shard has promised, or -1 when it has promised none
     */
    public long acceptedEpoch() {
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

    private long appendEntry(long epoch, String key, byte[] payload) {
        if (epoch < acceptedEpoch) {
            throw new StaleEpochException(epoch, acceptedEpoch);
        }
        promise(epoch);
        long ret = nextOffset;
        write(key == null ? ShardEntry.marker(ret, epoch) : new ShardEntry(ret, epoch, key, payload));
        return ret;
    }

    // The offset of the first held entry in the batch whose epoch differs from the owner's, or -1 when none does
    private long firstConflict(long from, List<ShardEntry> entries) {
        long ret = -1;
        long held = Math.min(nextOffset, from + entries.size()) - from;
        if (held > 0) {
            List<ShardEntry> heldEntries = read(from, (int) held, Long.MAX_VALUE);
            for (int i = 0; i < heldEntries.size(); i++) {
                if (heldEntries.get(i).epoch() != entries.get(i).epoch()) {
                    ret = from + i;
                    break;
                }
            }
        }
        return ret;
    }

    // Epochs never decrease along a shard, so the first entry of an epoch is found by binary search
    private long firstOffsetOfEpoch(long epoch, long atOrBefore) {
        long low = 0;
        long high = atOrBefore;
        while (low < high) {
            long middle = (low + high) >>> 1;
            if (epochAt(middle) < epoch) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private long epochAt(long offset) {
        return read(offset, 1, Long.MAX_VALUE).getFirst().epoch();
    }

    private void write(ShardEntry entry) {
        writeExcerpt(appender, entry);
        long index = appender.lastIndexAppended();
        cycleByFirstOffset.putIfAbsent(entry.offset() - rollCycle.toSequenceNumber(index), rollCycle.toCycle(index));
        lastEpoch = entry.epoch();
        // Written after the cycle map so a reader that sees the new offset also finds its cycle
        nextOffset = entry.offset() + 1;
    }

    private static void writeExcerpt(ExcerptAppender appender, ShardEntry entry) {
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
    }

    // Chronicle Queue cannot remove entries, so the entries before the offset are copied into a sibling directory
    // that then replaces this one. A complete copy of the shard is on disk at every step, which
    // completeInterruptedTruncation relies on after a crash.
    private void truncate(long offset) {
        Path copy = sibling(directory, TRUNCATING_SUFFIX);
        Path replaced = sibling(directory, REPLACED_SUFFIX);
        try {
            FileUtils.deleteDirectory(copy.toFile());
            try (SingleChronicleQueue target = build(copy);
                 ExcerptAppender targetAppender = target.createAppender();
                 ShardReader reader = newReader(0)) {
                long copied = 0;
                while (copied < offset) {
                    List<ShardEntry> batch = reader.read((int) Math.min(COPY_BATCH_SIZE, offset - copied), Long.MAX_VALUE);
                    if (batch.isEmpty()) {
                        throw new IllegalStateException("Truncating " + directory + " to " + offset + " found only " + copied + " entries");
                    }
                    batch.forEach(entry -> writeExcerpt(targetAppender, entry));
                    copied += batch.size();
                }
            }
            close();
            Files.move(directory, replaced, StandardCopyOption.ATOMIC_MOVE);
            Files.move(copy, directory, StandardCopyOption.ATOMIC_MOVE);
            FileUtils.deleteDirectory(replaced.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        open();
    }

    private void completeInterruptedTruncation() {
        Path copy = sibling(directory, TRUNCATING_SUFFIX);
        Path replaced = sibling(directory, REPLACED_SUFFIX);
        try {
            if (!Files.isDirectory(directory) && Files.isDirectory(copy)) {
                // The copy was complete when the shard was moved aside, so it becomes the shard
                Files.move(copy, directory, StandardCopyOption.ATOMIC_MOVE);
            }
            FileUtils.deleteDirectory(copy.toFile());
            FileUtils.deleteDirectory(replaced.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void open() {
        queue = build(directory);
        rollCycle = queue.rollCycle();
        appender = queue.createAppender();
        cycleByFirstOffset.clear();
        nextOffset = 0;
        lastEpoch = -1;
        if (queue.lastIndex() >= 0) {
            loadCycleOffsets();
        }
    }

    private static SingleChronicleQueue build(Path directory) {
        return SingleChronicleQueueBuilder.binary(directory)
                                          .rollCycle(RollCycles.FAST_DAILY)
                                          .build();
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

    private long readAcceptedEpoch() {
        Path file = sibling(directory, ACCEPTED_EPOCH_SUFFIX);
        long ret = -1;
        if (Files.exists(file)) {
            try {
                ret = Long.parseLong(Files.readString(file).trim());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return ret;
    }

    // A promise must survive power loss, or a node could accept entries from an owner it promised to refuse, so the
    // file and the rename that installs it are both forced to disk before the promise counts
    private void writeAcceptedEpoch(long epoch) {
        Path file = sibling(directory, ACCEPTED_EPOCH_SUFFIX);
        Path temp = sibling(directory, ACCEPTED_EPOCH_SUFFIX + ".tmp");
        try {
            Files.createDirectories(directory.getParent());
            // Written beside the target then moved, so a crash never leaves a partial epoch
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                                                        StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer content = ByteBuffer.wrap(String.valueOf(epoch).getBytes(StandardCharsets.UTF_8));
                while (content.hasRemaining()) {
                    channel.write(content);
                }
                channel.force(true);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel parent = FileChannel.open(directory.getParent(), StandardOpenOption.READ)) {
                parent.force(true);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path sibling(Path directory, String suffix) {
        return directory.resolveSibling(directory.getFileName() + suffix);
    }
}
