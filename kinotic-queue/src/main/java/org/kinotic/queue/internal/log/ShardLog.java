package org.kinotic.queue.internal.log;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.kinotic.queue.api.config.QueueProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
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
 * One shard of a queue on this node: an append-only log whose entries carry offsets that increase by one per entry,
 * and epochs that never decrease from one entry to the next. The oldest entries are deleted as the queue's retention
 * allows, so the shard begins at its {@link #start() start}. Writes are serialized; reads may run concurrently with
 * writes and with each other.
 */
@Slf4j
public final class ShardLog implements AutoCloseable {

    private static final String ACCEPTED_EPOCH_SUFFIX = ".accepted-epoch";
    private static final String MANIFEST_FILE = "segments";
    // The membership changes the shard holds, so its last one is known without reading the shard
    private static final String MEMBERSHIPS_FILE = "memberships";
    private static final String START_LINE = "start";
    // A segment is closed to writes once it is this large or old, or half the retention period old, so whole segments
    // can be deleted as the retention allows
    private static final long MAX_SEGMENT_BYTES = 1024L * 1024 * 1024;
    private static final Duration MAX_SEGMENT_AGE = Duration.ofDays(1);

    private final Path directory;
    private final boolean syncWrites;
    private final Duration retentionPeriod;
    // -1 when the shard's size is not limited
    private final long retentionBytes;
    // Readers hold the read lock while they use segments, so a truncation or deletion never closes a segment under a
    // reader
    private final ReentrantReadWriteLock segmentsLock = new ReentrantReadWriteLock();
    // Contiguous: each segment starts where the one before it ends. Replaced, never changed in place.
    private volatile List<ShardSegment> segments;
    private final ConcurrentSkipListMap<Long, ShardEntry> memberships = new ConcurrentSkipListMap<>();
    private volatile LogStart start = new LogStart(0, -1);
    private volatile long nextOffset;
    private volatile long syncedOffset;
    private volatile long lastEpoch;
    private volatile long acceptedEpoch;
    // The newest epoch an append failed in. Followers may hold the failed append's entries under that epoch, so its
    // offsets are only written again by an owner of a newer epoch, whose entries replace them.
    private long fencedEpoch = -1;

    /**
     * Opens the shard stored in {@code directory}, creating it empty when there is none, with the queue's storage
     * settings: whether writes are forced to disk, and how long and how large the shard is kept.
     */
    public ShardLog(Path directory, QueueProperties properties) {
        this.directory = directory;
        this.syncWrites = properties.isSyncWrites();
        this.retentionPeriod = properties.getRetentionPeriod();
        this.retentionBytes = properties.getRetentionBytes() != null ? properties.getRetentionBytes().toBytes() : -1;
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
     * @throws StaleEpochException when the shard has promised a newer owner, or an append of this epoch failed; a
     *                             failing append fences its epoch the same way
     */
    public synchronized List<Long> append(long epoch, List<ShardEntry> records) {
        requireCurrent(epoch);
        long first = nextOffset;
        List<Long> ret = new ArrayList<>(records.size());
        try {
            for (ShardEntry record : records) {
                ret.add(nextOffset);
                write(ShardEntry.record(nextOffset, epoch, record.key(), record.payload(), record.slot()));
            }
            sync();
        } catch (RuntimeException e) {
            fencedEpoch = Math.max(fencedEpoch, epoch);
            if (nextOffset > first) {
                truncate(first);
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
     * a commit; null when it holds none. Deleting old entries never deletes it.
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
     * holds past the owner's end are removed. A batch starting at the owner's start replaces every entry of a shard
     * that ends before it or differs from the owner there. The shard then deletes its segments that end by the
     * owner's start.
     *
     * @return how the batch was handled
     */
    public synchronized ReplicationResult replicate(ReplicationBatch batch) {
        ReplicationResult ret;
        if (batch.epoch() < acceptedEpoch) {
            ret = new ReplicationResult(ReplicationStatus.STALE_EPOCH, nextOffset);
        } else {
            promise(batch.epoch());
            long prevOffset = batch.prevOffset();
            boolean prevHeld = prevOffset >= start.offset() - 1 && prevOffset < nextOffset;
            boolean prevDiffers = prevHeld && epochAt(prevOffset) != batch.prevEpoch();
            if (prevOffset + 1 == batch.ownerStartOffset() && (prevOffset >= nextOffset || prevDiffers)) {
                // Nothing this copy holds continues the owner's entries, and the owner deleted those before its start
                reset(new LogStart(batch.ownerStartOffset(), batch.prevEpoch()));
                prevHeld = true;
                prevDiffers = false;
            }
            if (prevOffset >= nextOffset) {
                ret = new ReplicationResult(ReplicationStatus.MISMATCH, nextOffset);
            } else if (!prevHeld) {
                // The owner sent entries this copy deleted, which every copy holds the same, so it resends from this
                // copy's end
                ret = new ReplicationResult(ReplicationStatus.MISMATCH, nextOffset);
            } else if (prevDiffers) {
                // Every entry of the differing epoch is suspect, so the owner resends from that epoch's first entry; a
                // difference reaching this copy's start sends the owner back to its own start
                long epochStart = firstOffsetOfEpoch(epochAt(prevOffset), prevOffset);
                ret = new ReplicationResult(ReplicationStatus.MISMATCH, epochStart > start.offset() ? epochStart : 0);
            } else {
                ret = new ReplicationResult(ReplicationStatus.ACCEPTED, applyContinuing(batch));
            }
        }
        return ret;
    }

    /**
     * Reads entries starting at {@code offset}, stopping at {@code maxEntries} or once {@code maxBytes} is reached.
     * Always returns the entry at {@code offset} when there is one, however large.
     *
     * @param offset at least the {@link #start() start's} offset and at most {@link #nextOffset()}; reading at
     *               {@link #nextOffset()} returns no entries
     */
    public List<ShardEntry> read(long offset, int maxEntries, long maxBytes) {
        List<ShardEntry> ret = new ArrayList<>();
        segmentsLock.readLock().lock();
        try {
            if (offset < start.offset() || offset > nextOffset) {
                throw new IllegalArgumentException("Offset " + offset + " is outside " + directory + ", which holds offsets "
                                                           + start.offset() + " to " + nextOffset);
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
     * @param offset at least one before the {@link #start() start's} offset and before {@link #nextOffset()}
     * @return the epoch of the entry at {@code offset}, or -1 for offset -1
     */
    public long epochAt(long offset) {
        long ret;
        LogStart current = start;
        if (offset == current.offset() - 1) {
            ret = current.epochBefore();
        } else {
            ret = read(offset, 1, Long.MAX_VALUE).getFirst().epoch();
        }
        return ret;
    }

    /**
     * @return where the shard's entries begin
     */
    public LogStart start() {
        return start;
    }

    /**
     * @return the offset the next entry receives
     */
    public long nextOffset() {
        return nextOffset;
    }

    /**
     * @return the offset after the last entry of the last write that completed: forced to disk when the shard forces
     * its writes. Entries from it to {@link #nextOffset()} belong to a write in progress or to one that failed.
     */
    public long syncedOffset() {
        return syncedOffset;
    }

    /**
     * @return the epoch of the last entry, or of the last deleted one when the shard holds none; -1 when the shard
     * never held an entry
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

    /**
     * @return the offset before which the retention lets entries go: the end of the oldest segments whose entries are
     * all older than the retention period, or that the shard can lose while staying larger than the retention size.
     * The segment taking writes always stays.
     */
    public long retainedFrom() {
        List<ShardSegment> current = segments;
        long expiredBefore = System.currentTimeMillis() - retentionPeriod.toMillis();
        long size = current.stream().mapToLong(ShardSegment::bytes).sum();
        long ret = start.offset();
        for (int i = 0; i + 1 < current.size(); i++) {
            // Every entry of a segment was written before the next segment was created
            boolean expired = current.get(i + 1).createdMillis() <= expiredBefore;
            boolean oversized = retentionBytes >= 0 && size - current.get(i).bytes() >= retentionBytes;
            if (ret == current.get(i).startOffset() && (expired || oversized)) {
                ret = current.get(i).end();
                size -= current.get(i).bytes();
            }
        }
        return ret;
    }

    /**
     * Deletes the segments that end at or before {@code offset}, except the one holding the {@link #latestMembership()
     * latest membership change}, which the shard's owner writes again to let it go.
     *
     * @return the shard's start after the deletion
     */
    public synchronized LogStart deleteBefore(long offset) {
        ShardEntry latest = latestMembership();
        long limit = Math.min(offset, latest != null ? latest.offset() : Long.MAX_VALUE);
        List<ShardSegment> deleted = segments.stream().filter(segment -> segment.isSealed() && segment.end() <= limit).toList();
        if (!deleted.isEmpty()) {
            long newStart = deleted.getLast().end();
            LogStart updated = new LogStart(newStart, epochAt(newStart - 1));
            List<ShardSegment> kept = segments.subList(deleted.size(), segments.size());
            segmentsLock.writeLock().lock();
            try {
                // Recorded before anything changes on disk, so a crash leaves either the old shard or the shortened one
                writeManifest(updated, kept);
                start = updated;
                segments = List.copyOf(kept);
            } finally {
                segmentsLock.writeLock().unlock();
            }
            deleteQuietly(deleted);
            if (!memberships.headMap(newStart).isEmpty()) {
                memberships.headMap(newStart).clear();
                writeMemberships();
            }
            log.debug("Deleted the entries of shard {} before offset {}", directory, newStart);
        }
        return start;
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
            createDirectories(file.getParent());
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

    /**
     * Creates the directory and any missing parent, each of which survives a crash or power loss once this returns.
     */
    static void createDirectories(Path directory) {
        if (!Files.isDirectory(directory)) {
            createDirectories(directory.getParent());
            try {
                Files.createDirectory(directory);
            } catch (FileAlreadyExistsException e) {
                // Created concurrently; forcing the parent below still makes it durable
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            force(directory.getParent());
        }
    }

    private void requireCurrent(long epoch) {
        if (epoch < acceptedEpoch) {
            throw new StaleEpochException("Epoch " + epoch + " is older than the promised epoch " + acceptedEpoch);
        }
        if (epoch <= fencedEpoch) {
            throw new StaleEpochException("An append of epoch " + epoch + " failed, so the shard takes appends of a newer epoch only");
        }
        promise(epoch);
    }

    // Writes the batch's entries this copy lacks after the entry before the batch, which matches the owner's
    private long applyContinuing(ReplicationBatch batch) {
        long batchEnd = batch.prevOffset() + 1 + batch.entries().size();
        long conflict = firstConflict(batch.prevOffset() + 1, batch.entries());
        if (conflict >= 0) {
            truncate(conflict);
        }
        for (ShardEntry entry : batch.entries()) {
            if (entry.offset() == nextOffset) {
                write(entry);
            }
        }
        // Entries of the owner's epoch past the batch came from the owner after it read the batch, which a delayed
        // batch arriving after a later one finds; only entries of older epochs there are stale
        if (batchEnd == batch.ownerNextOffset() && nextOffset > batch.ownerNextOffset() && lastEpoch < batch.epoch()) {
            truncate(batch.ownerNextOffset());
        }
        // Also forces entries skipped as already held whose earlier sync failed
        sync();
        if (batch.ownerStartOffset() > start.offset()) {
            deleteBefore(batch.ownerStartOffset());
        }
        return batchEnd;
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
        long low = start.offset();
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

    private void write(ShardEntry entry) {
        try {
            if (entry.isMembership()) {
                memberships.put(entry.offset(), entry);
                // Listed before it is written, so a crash in between leaves a listing of an entry the shard lacks,
                // which open drops
                writeMemberships();
            }
            openSegment().write(entry);
        } catch (RuntimeException e) {
            if (entry.isMembership()) {
                memberships.remove(entry.offset());
            }
            throw e;
        }
        lastEpoch = entry.epoch();
        nextOffset = entry.offset() + 1;
    }

    private void sync() {
        if (syncedOffset < nextOffset) {
            if (syncWrites) {
                // Earlier segments hold unforced entries when a failed sync was followed by a truncation, or when the
                // write rolled to a new segment
                segments.forEach(ShardSegment::sync);
            }
            syncedOffset = nextOffset;
        }
    }

    // The segment writes go to, created at the shard's end when the last segment is sealed, too large or too old, or
    // when there is none
    private ShardSegment openSegment() {
        ShardSegment ret = segments.isEmpty() ? null : segments.getLast();
        if (ret != null && !ret.isSealed() && ret.end() > ret.startOffset() && isFull(ret)) {
            // Recorded in the manifest with the segment that follows it
            ret.seal(nextOffset);
        }
        if (ret == null || ret.isSealed()) {
            // Named uniquely, so a new segment never opens the directory of one deleted at the same offset
            String name = nextOffset + "-" + UUID.randomUUID();
            ShardSegment created = ShardSegment.open(directory.resolve(name), nextOffset, -1, System.currentTimeMillis(), 0);
            List<ShardSegment> updated = new ArrayList<>(segments);
            updated.add(created);
            try {
                // Recorded after the segment's directory exists and before anything is written to it; a crash in
                // between leaves an unlisted directory, which open deletes
                writeManifest(start, updated);
            } catch (RuntimeException e) {
                created.delete();
                throw e;
            }
            segments = List.copyOf(updated);
            ret = created;
        }
        return ret;
    }

    private boolean isFull(ShardSegment segment) {
        long maxAge = Math.min(MAX_SEGMENT_AGE.toMillis(), retentionPeriod.toMillis() / 2);
        long maxBytes = retentionBytes >= 0 ? Math.min(MAX_SEGMENT_BYTES, retentionBytes / 4) : MAX_SEGMENT_BYTES;
        return segment.bytes() >= maxBytes || segment.createdMillis() <= System.currentTimeMillis() - maxAge;
    }

    // Removes the entries from the offset on. Segments starting at or after it are deleted, and the segment holding it
    // is sealed there, so nothing is copied.
    private void truncate(long offset) {
        segmentsLock.writeLock().lock();
        try {
            List<ShardSegment> kept = segments.stream().filter(segment -> segment.startOffset() < offset).toList();
            List<ShardSegment> deleted = segments.stream().filter(segment -> segment.startOffset() >= offset).toList();
            if (!kept.isEmpty()) {
                kept.getLast().seal(offset);
            }
            // Recorded before anything changes on disk, so a crash leaves either the old shard or the truncated one
            writeManifest(start, kept);
            segments = kept;
            nextOffset = offset;
            syncedOffset = Math.min(syncedOffset, offset);
            lastEpoch = epochAt(offset - 1);
            boolean membershipsRemoved = !memberships.tailMap(offset).isEmpty();
            memberships.tailMap(offset).clear();
            deleteQuietly(deleted);
            if (membershipsRemoved) {
                writeMemberships();
            }
        } finally {
            segmentsLock.writeLock().unlock();
        }
    }

    // Deletes every entry and starts the shard over at the start, with no segment until the next write
    private void reset(LogStart newStart) {
        List<ShardSegment> deleted = segments;
        segmentsLock.writeLock().lock();
        try {
            writeManifest(newStart, List.of());
            start = newStart;
            segments = List.of();
            nextOffset = newStart.offset();
            syncedOffset = newStart.offset();
            lastEpoch = newStart.epochBefore();
        } finally {
            segmentsLock.writeLock().unlock();
        }
        deleteQuietly(deleted);
        if (!memberships.isEmpty()) {
            memberships.clear();
            writeMemberships();
        }
        log.info("Shard {} started over at offset {}, the start of its owner's entries", directory, newStart.offset());
    }

    // The manifest is written before segments are deleted, so a failed deletion leaves an unlisted directory, which
    // open deletes
    private void deleteQuietly(List<ShardSegment> deleted) {
        for (ShardSegment segment : deleted) {
            try {
                segment.delete();
            } catch (RuntimeException e) {
                log.warn("Deleting segment {} of shard {} failed; it is deleted when the shard is opened again",
                         segment.name(), directory, e);
            }
        }
    }

    // A first line with the shard's start offset and the epoch before it, then one line per segment, in offset order:
    // its directory name, start offset, the offset it was sealed at or -1, when it was created, and its size in bytes
    private void writeManifest(LogStart logStart, List<ShardSegment> manifestSegments) {
        writeDurably(directory.resolve(MANIFEST_FILE), String.join("\n", manifestLines(logStart, manifestSegments)));
    }

    private static List<String> manifestLines(LogStart logStart, List<ShardSegment> manifestSegments) {
        List<String> ret = new ArrayList<>();
        ret.add(START_LINE + " " + logStart.offset() + " " + logStart.epochBefore());
        for (ShardSegment segment : manifestSegments) {
            ret.add(segment.name() + " " + segment.startOffset() + " " + (segment.isSealed() ? segment.end() : -1) + " "
                            + segment.createdMillis() + " " + segment.bytes());
        }
        return ret;
    }

    private void openSegments() {
        List<ShardSegment> opened = new ArrayList<>();
        Path manifest = directory.resolve(MANIFEST_FILE);
        LogStart stored = new LogStart(0, -1);
        try {
            createDirectories(directory);
            if (Files.exists(manifest)) {
                for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                    String[] fields = line.trim().split(" ");
                    if (fields[0].equals(START_LINE)) {
                        stored = new LogStart(Long.parseLong(fields[1]), Long.parseLong(fields[2]));
                    } else if (!line.isBlank()) {
                        opened.add(ShardSegment.open(directory.resolve(fields[0]),
                                                     Long.parseLong(fields[1]),
                                                     Long.parseLong(fields[2]),
                                                     Long.parseLong(fields[3]),
                                                     Long.parseLong(fields[4])));
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
        start = stored;
        segments = List.copyOf(contiguous(opened));
        nextOffset = segments.isEmpty() ? start.offset() : segments.getLast().end();
        syncedOffset = nextOffset;
        lastEpoch = epochAt(nextOffset - 1);
    }

    // The segments up to the first that starts past the end of the one before it, which happens when a crash lost
    // entries that were never forced to disk. The manifest is rewritten when segments were dropped or end earlier.
    private List<ShardSegment> contiguous(List<ShardSegment> opened) {
        List<ShardSegment> ret = new ArrayList<>();
        List<ShardSegment> dropped = new ArrayList<>();
        long expectedStart = start.offset();
        for (ShardSegment segment : opened) {
            if (dropped.isEmpty() && segment.startOffset() == expectedStart) {
                ret.add(segment);
                expectedStart = segment.end();
            } else {
                dropped.add(segment);
            }
        }
        if (!dropped.isEmpty()) {
            log.warn("Shard {} lost the entries from offset {} on, which were not forced to disk before a crash", directory, expectedStart);
        }
        Path manifest = directory.resolve(MANIFEST_FILE);
        try {
            if (Files.exists(manifest) && !manifestLines(start, ret).equals(Files.readAllLines(manifest, StandardCharsets.UTF_8))) {
                writeManifest(start, ret);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        deleteQuietly(dropped);
        return ret;
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
            // Listings of entries the shard lacks: written past a crash, for a write that failed, or deleted
            boolean dropped = memberships.values().removeIf(listed -> {
                boolean readable = listed.offset() >= start.offset() && listed.offset() < nextOffset;
                List<ShardEntry> held = readable ? read(listed.offset(), 1, Long.MAX_VALUE) : List.of();
                return held.isEmpty() || !held.getFirst().isMembership() || held.getFirst().epoch() != listed.epoch();
            });
            if (dropped) {
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
