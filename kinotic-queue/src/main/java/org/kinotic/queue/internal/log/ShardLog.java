package org.kinotic.queue.internal.log;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * One shard of a queue on this node: an append-only log whose entries carry offsets that start at zero and
 * increase by one per entry, and epochs that never decrease from one entry to the next. Writes are serialized; reads
 * may run concurrently with writes and with each other.
 */
public final class ShardLog implements AutoCloseable {

    private static final String ACCEPTED_EPOCH_SUFFIX = ".accepted-epoch";
    private static final String MANIFEST_FILE = "segments";
    // The membership changes the shard holds, so its last one is known without reading the shard
    private static final String MEMBERSHIPS_FILE = "memberships";

    private final Path directory;
    private final boolean syncWrites;
    // Readers hold the read lock while they use segments, so a truncation never closes a segment under a reader
    private final ReentrantReadWriteLock segmentsLock = new ReentrantReadWriteLock();
    // Contiguous: each segment starts where the one before it ends. Replaced, never changed in place.
    private volatile List<ShardSegment> segments;
    private final ConcurrentSkipListMap<Long, ShardEntry> memberships = new ConcurrentSkipListMap<>();
    private volatile long nextOffset;
    private volatile long lastEpoch;
    private volatile long acceptedEpoch;

    /**
     * Opens the shard stored in {@code directory}, creating it empty when there is none.
     *
     * @param syncWrites whether each write is forced to disk before the method that wrote it returns
     */
    public ShardLog(Path directory, boolean syncWrites) {
        this.directory = directory;
        this.syncWrites = syncWrites;
        openSegments();
        loadMemberships();
        acceptedEpoch = Math.max(readAcceptedEpoch(directory), lastEpoch);
    }

    /**
     * @return whether a shard is stored in {@code directory}
     */
    public static boolean exists(Path directory) {
        return Files.isDirectory(directory);
    }

    /**
     * Promises, for a shard this node stores no copy of, not to accept entries from an owner older than
     * {@code epoch}. A copy created in {@code directory} later keeps the promise. The promise survives restarts.
     *
     * @return the newest epoch promised for the shard before this call, or -1 when none was
     */
    static long promiseWithoutCopy(Path directory, long epoch) {
        long ret = readAcceptedEpoch(directory);
        if (epoch > ret) {
            writeDurably(sibling(directory, ACCEPTED_EPOCH_SUFFIX), String.valueOf(epoch));
        }
        return ret;
    }

    /**
     * Appends records as the shard's owner, one after another, and forces them to disk. Either every record is
     * appended or, when the method throws, none is.
     *
     * @param epoch   the owner's epoch
     * @param records the records, each with its key, payload and slot set; their offsets and epochs are ignored
     * @return the offset each record was written at, in the order of {@code records}
     * @throws StaleEpochException when the shard has promised a newer owner
     */
    public synchronized List<Long> append(long epoch, List<ShardEntry> records) {
        requireCurrent(epoch);
        long start = nextOffset;
        List<Long> ret = new ArrayList<>(records.size());
        try {
            for (ShardEntry record : records) {
                ret.add(nextOffset);
                write(ShardEntry.record(nextOffset, epoch, record.key(), record.payload(), record.slot()));
            }
            sync();
        } catch (RuntimeException e) {
            if (nextOffset > start) {
                truncate(start);
            }
            throw e;
        }
        return ret;
    }

    /**
     * Appends the marker an owner writes first in its epoch.
     *
     * @return the offset the marker was written at
     * @throws StaleEpochException when the shard has promised a newer owner
     */
    public synchronized long appendMarker(long epoch) {
        requireCurrent(epoch);
        long ret = nextOffset;
        write(ShardEntry.marker(ret, epoch));
        sync();
        return ret;
    }

    /**
     * Appends a change of the copies whose majority counts toward a commit, as the shard's owner.
     *
     * @return the offset the change was written at
     * @throws StaleEpochException when the shard has promised a newer owner
     */
    public synchronized long appendMembership(long epoch, Membership membership) {
        requireCurrent(epoch);
        long ret = nextOffset;
        write(ShardEntry.membership(ret, epoch, membership));
        sync();
        return ret;
    }

    /**
     * @return the last membership change this copy holds, committed or not, which decides the copies counting toward
     * a commit; null when it holds none
     */
    public ShardEntry latestMembership() {
        Map.Entry<Long, ShardEntry> ret = memberships.lastEntry();
        return ret != null ? ret.getValue() : null;
    }

    /**
     * Promises not to accept entries from an owner older than {@code epoch}. The promise survives restarts.
     *
     * @return the newest epoch the shard had promised before this call, or -1 when it had promised none
     */
    public synchronized long promise(long epoch) {
        long ret = acceptedEpoch;
        if (epoch > acceptedEpoch) {
            writeDurably(sibling(directory, ACCEPTED_EPOCH_SUFFIX), String.valueOf(epoch));
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
                boolean written = false;
                for (ShardEntry entry : entries) {
                    if (entry.offset() == nextOffset) {
                        write(entry);
                        written = true;
                    }
                }
                // Entries of the owner's epoch past the batch came from the owner after it read the batch, which a
                // delayed batch arriving after a later one finds; only entries of older epochs there are stale
                if (batchEnd == ownerNextOffset && nextOffset > ownerNextOffset && lastEpoch < epoch) {
                    truncate(ownerNextOffset);
                }
                if (written) {
                    sync();
                }
                ret = new ReplicationResult(ReplicationStatus.ACCEPTED, batchEnd);
            }
        }
        return ret;
    }

    /**
     * Reads entries starting at {@code offset}, stopping at {@code maxEntries} or once {@code maxBytes} is reached.
     * Always returns the entry at {@code offset} when there is one, however large.
     *
     * @param offset at most {@link #nextOffset()}; reading at {@link #nextOffset()} returns no entries
     */
    public List<ShardEntry> read(long offset, int maxEntries, long maxBytes) {
        List<ShardEntry> ret = new ArrayList<>();
        segmentsLock.readLock().lock();
        try {
            if (offset < 0 || offset > nextOffset) {
                throw new IllegalArgumentException("Offset " + offset + " is outside " + directory + ", which ends at " + nextOffset);
            }
            long bytesRead = 0;
            for (ShardSegment segment : segments) {
                long from = offset + ret.size();
                if (ret.size() < maxEntries && bytesRead < maxBytes && from >= segment.startOffset() && from < segment.end()) {
                    List<ShardEntry> read = segment.read(from, maxEntries - ret.size(), maxBytes - bytesRead);
                    ret.addAll(read);
                    bytesRead += read.stream().mapToLong(ShardEntry::size).sum();
                }
            }
        } finally {
            segmentsLock.readLock().unlock();
        }
        return ret;
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
        segmentsLock.writeLock().lock();
        try {
            segments.forEach(ShardSegment::close);
        } finally {
            segmentsLock.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        return directory.toString();
    }

    /**
     * Forces a file or directory to disk. On Linux a file's fsync also writes the pages changed through a memory
     * mapping of it.
     */
    static void force(Path path) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Replaces the file's content so that, after a crash or power loss, the file holds either its old content or
     * {@code content}, never part of it.
     */
    static void writeDurably(Path file, String content) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                                                        StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            force(file.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void requireCurrent(long epoch) {
        if (epoch < acceptedEpoch) {
            throw new StaleEpochException(epoch, acceptedEpoch);
        }
        promise(epoch);
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
        if (entry.isMembership()) {
            memberships.put(entry.offset(), entry);
            // Listed before it is written, so a crash in between leaves a listing past the shard's end, which open drops
            writeMemberships();
        }
        openSegment().write(entry);
        lastEpoch = entry.epoch();
        nextOffset = entry.offset() + 1;
    }

    private void sync() {
        if (syncWrites && !segments.isEmpty()) {
            segments.getLast().sync();
        }
    }

    // The segment writes go to, created at the shard's end when the last segment is sealed or there is none
    private ShardSegment openSegment() {
        ShardSegment ret = segments.isEmpty() ? null : segments.getLast();
        if (ret == null || ret.isSealed()) {
            // Named uniquely, so a new segment never opens the directory of one deleted at the same offset
            String name = nextOffset + "-" + UUID.randomUUID();
            List<String> lines = new ArrayList<>(segments.stream().map(ShardLog::manifestLine).toList());
            lines.add(manifestLine(name, nextOffset, -1));
            // Recorded before the segment's directory exists, so no directory outlives a crash without being listed
            writeManifest(lines);
            ret = ShardSegment.open(directory.resolve(name), nextOffset, -1);
            List<ShardSegment> updated = new ArrayList<>(segments);
            updated.add(ret);
            segments = List.copyOf(updated);
        }
        return ret;
    }

    // Removes the entries from the offset on. Segments starting at or after it are deleted, and the segment holding it
    // is sealed there, so nothing is copied.
    private void truncate(long offset) {
        segmentsLock.writeLock().lock();
        try {
            List<ShardSegment> kept = segments.stream().filter(segment -> segment.startOffset() < offset).toList();
            List<ShardSegment> deleted = segments.stream().filter(segment -> segment.startOffset() >= offset).toList();
            List<String> lines = new ArrayList<>(kept.stream().map(ShardLog::manifestLine).toList());
            if (!kept.isEmpty()) {
                ShardSegment holding = kept.getLast();
                lines.set(lines.size() - 1, manifestLine(holding.name(), holding.startOffset(), offset));
            }
            // Recorded before anything changes on disk, so a crash leaves either the old shard or the truncated one
            writeManifest(lines);
            if (!kept.isEmpty()) {
                kept.getLast().seal(offset);
            }
            segments = kept;
            deleted.forEach(ShardSegment::delete);
            if (!memberships.tailMap(offset).isEmpty()) {
                memberships.tailMap(offset).clear();
                writeMemberships();
            }
            nextOffset = offset;
            lastEpoch = offset > 0 ? epochAt(offset - 1) : -1;
        } finally {
            segmentsLock.writeLock().unlock();
        }
    }

    // One line per segment, in offset order: its directory name, start offset, and the offset it was sealed at or -1
    private void writeManifest(List<String> lines) {
        writeDurably(directory.resolve(MANIFEST_FILE), String.join("\n", lines));
    }

    private static String manifestLine(ShardSegment segment) {
        return manifestLine(segment.name(), segment.startOffset(), segment.isSealed() ? segment.end() : -1);
    }

    private static String manifestLine(String name, long startOffset, long sealedEnd) {
        return name + " " + startOffset + " " + sealedEnd;
    }

    private void openSegments() {
        List<ShardSegment> opened = new ArrayList<>();
        Path manifest = directory.resolve(MANIFEST_FILE);
        try {
            Files.createDirectories(directory);
            if (Files.exists(manifest)) {
                for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        String[] fields = line.trim().split(" ");
                        opened.add(ShardSegment.open(directory.resolve(fields[0]), Long.parseLong(fields[1]), Long.parseLong(fields[2])));
                    }
                }
            }
            // Directories of segments deleted or never listed before a crash
            Set<String> listed = new HashSet<>(opened.stream().map(ShardSegment::name).toList());
            try (Stream<Path> children = Files.list(directory)) {
                for (Path child : children.toList()) {
                    String name = child.getFileName().toString();
                    if (Files.isDirectory(child) && !listed.contains(name)) {
                        FileUtils.deleteDirectory(child.toFile());
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        segments = List.copyOf(opened);
        nextOffset = opened.isEmpty() ? 0 : opened.getLast().end();
        lastEpoch = nextOffset > 0 ? epochAt(nextOffset - 1) : -1;
    }

    private void writeMemberships() {
        List<String> lines = new ArrayList<>();
        for (ShardEntry entry : memberships.values()) {
            lines.add(entry.offset() + " " + entry.epoch() + " " + storageIds(entry.membership().voting()) + " "
                              + storageIds(entry.membership().joining()));
        }
        writeDurably(directory.resolve(MEMBERSHIPS_FILE), String.join("\n", lines));
    }

    private void loadMemberships() {
        Path file = directory.resolve(MEMBERSHIPS_FILE);
        if (Files.exists(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        String[] fields = line.trim().split(" ");
                        long offset = Long.parseLong(fields[0]);
                        Membership membership = new Membership(storageIds(fields[2]), storageIds(fields[3]));
                        memberships.put(offset, ShardEntry.membership(offset, Long.parseLong(fields[1]), membership));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (!memberships.tailMap(nextOffset).isEmpty()) {
                memberships.tailMap(nextOffset).clear();
                writeMemberships();
            }
        }
    }

    // Storage ids are UUIDs, so a comma separates them; "-" stands for none
    private static String storageIds(List<String> storageIds) {
        return storageIds.isEmpty() ? "-" : String.join(",", storageIds);
    }

    private static List<String> storageIds(String field) {
        return field.equals("-") ? List.of() : List.of(field.split(","));
    }

    private static long readAcceptedEpoch(Path directory) {
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

    private static Path sibling(Path directory, String suffix) {
        return directory.resolveSibling(directory.getFileName() + suffix);
    }
}
