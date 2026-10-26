package org.kinotic.queue.internal.log;

import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import org.apache.commons.io.FileUtils;
import org.kinotic.queue.api.config.QueueProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

/**
 * One shard of a queue on this node: an append-only log whose entries carry offsets that increase by one per entry,
 * and epochs that never decrease from one entry to the next. The oldest entries are deleted as the queue's retention
 * allows, so the shard begins at its {@link #start() start}. Writes are serialized; reads may run concurrently with
 * writes and with each other. A closed shard refuses every change with an {@link IllegalStateException}.
 */
@Slf4j
@Accessors(fluent = true)
public final class ShardLog implements AutoCloseable {

    private static final String MANIFEST_FILE = "segments";
    private static final String START_LINE = "start";
    private static final String SEGMENT_LINE = "segment";
    // The membership changes the shard holds are listed too, so its last one is known without reading the shard
    private static final String MEMBERSHIP_LINE = "membership";
    // A segment is closed to writes once it is this large or old, or half the retention period old, so whole segments
    // can be deleted as the retention allows
    private static final long MAX_SEGMENT_BYTES = 1024L * 1024 * 1024;
    private static final long MIN_SEGMENT_BYTES = 64 * 1024;
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
    /**
     * Where the shard's entries begin.
     */
    @Getter
    private volatile LogStart start = new LogStart(0, -1);
    /**
     * The offset the next entry receives.
     */
    @Getter
    private volatile long nextOffset;
    /**
     * The offset after the last entry of the last write that completed: forced to disk when the shard forces its
     * writes. Entries from it to {@link #nextOffset()} belong to a write in progress or to one that failed.
     */
    @Getter
    private volatile long syncedOffset;
    /**
     * The epoch of the last entry, or of the last deleted one when the shard holds none; -1 when the shard never held
     * an entry.
     */
    @Getter
    private volatile long lastEpoch;
    /**
     * The newest epoch the shard has promised, or -1 when it has promised none.
     */
    @Getter
    private volatile long acceptedEpoch;
    // The newest epoch an append failed in. Followers may hold the failed append's entries under that epoch, so its
    // offsets are only written again by an owner of a newer epoch, whose entries replace them.
    private long fencedEpoch = -1;
    private boolean closed;

    /**
     * Opens the shard stored in {@code directory}, creating it empty when there is none, with the queue's storage
     * settings: whether writes are forced to disk, and how long and how large the shard is kept.
     */
    ShardLog(Path directory, QueueProperties properties) {
        this.directory = directory;
        this.syncWrites = properties.isSyncWrites();
        this.retentionPeriod = properties.getRetentionPeriod();
        this.retentionBytes = properties.getRetentionBytes() != null ? properties.getRetentionBytes().toBytes() : -1;
        open();
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
            DurableFiles.writeDurably(acceptedEpochFile(directory), String.valueOf(epoch));
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
        return appendOwned(epoch, offset -> ShardEntry.marker(offset, epoch));
    }

    /**
     * Appends a change of the copies whose majority counts toward a commit, as the shard's owner.
     *
     * @return the offset the change was written at
     * @throws StaleEpochException when the shard has promised a newer owner
     */
    public synchronized long appendMembership(long epoch, Membership membership) {
        return appendOwned(epoch, offset -> ShardEntry.membership(offset, epoch, membership));
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
        requireOpen();
        long ret = acceptedEpoch;
        if (epoch > acceptedEpoch) {
            DurableFiles.writeDurably(acceptedEpochFile(directory), String.valueOf(epoch));
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
        requireOpen();
        ReplicationResult ret;
        if (batch.epoch() < acceptedEpoch) {
            ret = new ReplicationResult(ReplicationStatus.STALE_EPOCH, nextOffset);
        } else {
            promise(batch.epoch());
            long prevOffset = batch.prevOffset();
            boolean prevHeld = prevOffset >= start.offset() - 1 && prevOffset < nextOffset;
            long heldEpoch = prevHeld ? heldEpochAt(prevOffset) : -1;
            boolean prevDiffers = prevHeld && heldEpoch != batch.prevEpoch();
            if (prevOffset + 1 == batch.ownerStartOffset() && (prevOffset >= nextOffset || prevDiffers)) {
                // Nothing this copy holds continues the owner's entries, and the owner deleted those before its start,
                // so the copy starts over where the batch continues
                reset(new LogStart(batch.ownerStartOffset(), batch.prevEpoch()));
                ret = new ReplicationResult(ReplicationStatus.ACCEPTED, applyContinuing(batch));
            } else if (prevOffset >= nextOffset) {
                ret = new ReplicationResult(ReplicationStatus.MISMATCH, nextOffset);
            } else if (!prevHeld) {
                // The owner sent entries this copy deleted. Copies only delete committed entries, which every copy holds
                // the same, so the owner resends from this copy's start.
                ret = new ReplicationResult(ReplicationStatus.MISMATCH, start.offset());
            } else if (prevDiffers) {
                // Every entry of the differing epoch is suspect from that epoch's first entry here, or this copy's start
                ret = new ReplicationResult(ReplicationStatus.MISMATCH, firstOffsetOfEpoch(heldEpoch, prevOffset), heldEpoch);
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
        return read(offset, maxEntries, maxBytes, ShardSegment::decodeEntry, ShardEntry::size);
    }

    /**
     * Reads where each entry starting at {@code offset} sat in the batch it was appended in, without reading entries'
     * keys and payloads, stopping at {@code maxEntries}.
     *
     * @param offset as for {@link #read}
     */
    public List<RecordSlot> readSlots(long offset, int maxEntries) {
        return read(offset, maxEntries, Long.MAX_VALUE, ShardSegment::decodeSlot, RecordSlot::size);
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
            ret = read(offset, 1, Long.MAX_VALUE, ShardSegment::decodeEpoch, epoch -> 0).getFirst();
        }
        return ret;
    }

    private <T> List<T> read(long offset, int maxEntries, long maxBytes, BiFunction<Long, Bytes<?>, T> decoder, ToLongFunction<T> size) {
        List<T> ret = new ArrayList<>();
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
                    List<T> read = segment.read(from, maxEntries - ret.size(), maxBytes - bytesRead, decoder, size);
                    ret.addAll(read);
                    bytesRead += read.stream().mapToLong(size).sum();
                }
            }
        } finally {
            segmentsLock.readLock().unlock();
        }
        return ret;
    }

    /**
     * @return the offset after the shard's last entry of an epoch no newer than {@code epoch}, or the start's offset
     * when it holds none
     */
    public long endOfEpoch(long epoch) {
        return firstOffsetOfEpoch(epoch + 1, nextOffset);
    }

    /**
     * @return about how many bytes the shard's entries take: the {@link ShardEntry#size sizes} of the entries written
     * to its segments
     */
    public long bytes() {
        return segments.stream().mapToLong(ShardSegment::bytes).sum();
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
        requireOpen();
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
                writeManifest(updated, kept, memberships.tailMap(newStart).values());
                start = updated;
                segments = List.copyOf(kept);
                memberships.headMap(newStart).clear();
            } finally {
                segmentsLock.writeLock().unlock();
            }
            deleteQuietly(deleted);
            log.debug("Deleted the entries of shard {} before offset {}", directory, newStart);
        }
        return start;
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            segmentsLock.writeLock().lock();
            try {
                // Records how large the segment taking writes has grown, which the manifest otherwise records when it closes
                try {
                    writeManifest(start, segments, memberships.values());
                } catch (RuntimeException e) {
                    log.warn("Recording the segments of shard {} failed", directory, e);
                }
                segments.forEach(ShardSegment::close);
            } finally {
                segmentsLock.writeLock().unlock();
            }
        }
    }

    @Override
    public String toString() {
        return directory.toString();
    }

    // A closed shard's directory may already hold the copy of a queue created later with the same name
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Shard " + directory + " is closed");
        }
    }

    private void requireCurrent(long epoch) {
        requireOpen();
        if (epoch < acceptedEpoch) {
            throw new StaleEpochException("Epoch " + epoch + " is older than the promised epoch " + acceptedEpoch);
        }
        if (epoch <= fencedEpoch) {
            throw new StaleEpochException("An append of epoch " + epoch + " failed, so the shard takes appends of a newer epoch only");
        }
        promise(epoch);
    }

    // Appends an entry of the owner's own at the shard's end, given the offset it is written at
    private long appendOwned(long epoch, LongFunction<ShardEntry> entryAt) {
        requireCurrent(epoch);
        long ret = nextOffset;
        write(entryAt.apply(ret));
        sync();
        return ret;
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

    // Called with this shard's lock held, so the last entry's epoch is the one recorded for it
    private long heldEpochAt(long offset) {
        return offset == nextOffset - 1 ? lastEpoch : epochAt(offset);
    }

    // The offset of the first held entry in the batch whose epoch differs from the owner's, or -1 when none does
    private long firstConflict(long from, List<ShardEntry> entries) {
        long ret = -1;
        long held = Math.min(nextOffset, from + entries.size()) - from;
        if (held > 0) {
            List<Long> heldEpochs = read(from, (int) held, Long.MAX_VALUE, ShardSegment::decodeEpoch, epoch -> 0);
            for (int i = 0; i < heldEpochs.size(); i++) {
                if (heldEpochs.get(i) != entries.get(i).epoch()) {
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
        ShardSegment segment = openSegment();
        try {
            if (entry.isMembership()) {
                memberships.put(entry.offset(), entry);
                // Listed before it is written, so a crash in between leaves a listing of an entry the shard lacks,
                // which open drops
                writeManifest(start, segments, memberships.values());
            }
            segment.write(entry);
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
        boolean rolled = ret != null && !ret.isSealed() && ret.end() > ret.startOffset() && isFull(ret);
        if (rolled) {
            // Recorded in the manifest with the segment that follows it
            ret.seal(nextOffset);
        }
        if (ret == null || ret.isSealed()) {
            // Named uniquely, so a new segment never opens the directory of one deleted at the same offset
            String name = nextOffset + "-" + UUID.randomUUID();
            ShardSegment created = new ShardSegment(directory.resolve(name), nextOffset, -1, System.currentTimeMillis(), 0);
            List<ShardSegment> updated = new ArrayList<>(segments);
            updated.add(created);
            try {
                // Recorded after the segment's directory exists and before anything is written to it; a crash in
                // between leaves an unlisted directory, which open deletes
                writeManifest(start, updated, memberships.values());
            } catch (RuntimeException e) {
                created.delete();
                if (rolled) {
                    ret.seal(-1);
                }
                throw e;
            }
            segments = List.copyOf(updated);
            ret = created;
        }
        return ret;
    }

    private boolean isFull(ShardSegment segment) {
        long maxAge = Math.min(MAX_SEGMENT_AGE.toMillis(), retentionPeriod.toMillis() / 2);
        long maxBytes = retentionBytes >= 0 ? Math.clamp(retentionBytes / 4, MIN_SEGMENT_BYTES, MAX_SEGMENT_BYTES) : MAX_SEGMENT_BYTES;
        return segment.bytes() >= maxBytes || segment.createdMillis() <= System.currentTimeMillis() - maxAge;
    }

    // Removes the entries from the offset on. Segments starting at or after it are deleted, and the segment holding it
    // is sealed there, so nothing is copied.
    private void truncate(long offset) {
        segmentsLock.writeLock().lock();
        try {
            List<ShardSegment> kept = segments.stream().filter(segment -> segment.startOffset() < offset).toList();
            List<ShardSegment> deleted = segments.stream().filter(segment -> segment.startOffset() >= offset).toList();
            ShardSegment holding = kept.isEmpty() ? null : kept.getLast();
            long sealedBefore = holding != null && holding.isSealed() ? holding.end() : -1;
            if (holding != null) {
                holding.seal(offset);
            }
            // Recorded before anything changes on disk, so a crash leaves either the old shard or the truncated one
            try {
                writeManifest(start, kept, memberships.headMap(offset).values());
            } catch (RuntimeException e) {
                if (holding != null) {
                    holding.seal(sealedBefore);
                }
                throw e;
            }
            segments = kept;
            nextOffset = offset;
            syncedOffset = Math.min(syncedOffset, offset);
            lastEpoch = epochAt(offset - 1);
            memberships.tailMap(offset).clear();
            deleteQuietly(deleted);
        } finally {
            segmentsLock.writeLock().unlock();
        }
    }

    // Deletes every entry and starts the shard over at the start, with no segment until the next write
    private void reset(LogStart newStart) {
        List<ShardSegment> deleted = segments;
        segmentsLock.writeLock().lock();
        try {
            writeManifest(newStart, List.of(), List.of());
            start = newStart;
            segments = List.of();
            memberships.clear();
            nextOffset = newStart.offset();
            syncedOffset = newStart.offset();
            lastEpoch = newStart.epochBefore();
        } finally {
            segmentsLock.writeLock().unlock();
        }
        deleteQuietly(deleted);
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

    // A first line with the shard's start offset and the epoch before it, one line per segment, in offset order, with
    // its directory name, start offset, the offset it was sealed at or -1, when it was created and its size in bytes,
    // then one line per membership change, in offset order, with its offset, epoch, and voting and joining copies
    private void writeManifest(LogStart logStart, List<ShardSegment> manifestSegments, Collection<ShardEntry> manifestMemberships) {
        DurableFiles.writeDurably(directory.resolve(MANIFEST_FILE),
                                  String.join("\n", manifestLines(logStart, manifestSegments, manifestMemberships)));
    }

    private static List<String> manifestLines(LogStart logStart, List<ShardSegment> manifestSegments, Collection<ShardEntry> manifestMemberships) {
        List<String> ret = new ArrayList<>();
        ret.add(START_LINE + " " + logStart.offset() + " " + logStart.epochBefore());
        for (ShardSegment segment : manifestSegments) {
            ret.add(SEGMENT_LINE + " " + segment.name() + " " + segment.startOffset() + " " + (segment.isSealed() ? segment.end() : -1)
                            + " " + segment.createdMillis() + " " + segment.bytes());
        }
        for (ShardEntry entry : manifestMemberships) {
            ret.add(MEMBERSHIP_LINE + " " + entry.offset() + " " + entry.epoch() + " " + storageIds(entry.membership().voting())
                            + " " + storageIds(entry.membership().joining()));
        }
        return ret;
    }

    private void open() {
        Path manifest = directory.resolve(MANIFEST_FILE);
        List<String> stored = DurableFiles.readLines(manifest);
        List<ShardSegment> opened = new ArrayList<>();
        List<ShardEntry> listed = new ArrayList<>();
        try {
            DurableFiles.createDirectories(directory);
            for (String line : stored) {
                String[] fields = line.trim().split(" ");
                switch (fields[0]) {
                    case START_LINE -> start = new LogStart(Long.parseLong(fields[1]), Long.parseLong(fields[2]));
                    case SEGMENT_LINE -> opened.add(new ShardSegment(directory.resolve(fields[1]),
                                                                     Long.parseLong(fields[2]),
                                                                     Long.parseLong(fields[3]),
                                                                     Long.parseLong(fields[4]),
                                                                     Long.parseLong(fields[5])));
                    case MEMBERSHIP_LINE -> {
                        long offset = Long.parseLong(fields[1]);
                        Membership membership = new Membership(storageIds(fields[3]), storageIds(fields[4]));
                        listed.add(ShardEntry.membership(offset, Long.parseLong(fields[2]), membership));
                    }
                    default -> {
                        // A blank line
                    }
                }
            }
            // Directories of segments deleted or never listed before a crash
            Set<String> names = new HashSet<>(opened.stream().map(ShardSegment::name).toList());
            try (Stream<Path> children = Files.list(directory)) {
                for (Path child : children.toList()) {
                    if (Files.isDirectory(child) && !names.contains(child.getFileName().toString())) {
                        FileUtils.deleteDirectory(child.toFile());
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        segments = contiguous(opened);
        nextOffset = segments.isEmpty() ? start.offset() : segments.getLast().end();
        syncedOffset = nextOffset;
        lastEpoch = epochAt(nextOffset - 1);
        // Listings of entries the shard lacks: written past a crash, for a write that failed, or lost with dropped
        // segments
        for (ShardEntry entry : listed) {
            List<ShardEntry> held = entry.offset() >= start.offset() && entry.offset() < nextOffset
                    ? read(entry.offset(), 1, Long.MAX_VALUE) : List.of();
            if (!held.isEmpty() && held.getFirst().isMembership() && held.getFirst().epoch() == entry.epoch()) {
                memberships.put(entry.offset(), entry);
            }
        }
        if (!stored.isEmpty() && !manifestLines(start, segments, memberships.values()).equals(stored)) {
            writeManifest(start, segments, memberships.values());
        }
        deleteQuietly(opened.subList(segments.size(), opened.size()));
    }

    // The segments up to the first that starts past the end of the one before it, which happens when a crash lost
    // entries that were never forced to disk
    private List<ShardSegment> contiguous(List<ShardSegment> opened) {
        int kept = 0;
        long expectedStart = start.offset();
        while (kept < opened.size() && opened.get(kept).startOffset() == expectedStart) {
            expectedStart = opened.get(kept).end();
            kept++;
        }
        if (kept < opened.size()) {
            log.warn("Shard {} lost the entries from offset {} on, which were not forced to disk before a crash", directory, expectedStart);
        }
        return List.copyOf(opened.subList(0, kept));
    }

    // Storage ids are UUIDs, so a comma separates them; "-" stands for none
    private static String storageIds(List<String> storageIds) {
        return storageIds.isEmpty() ? "-" : String.join(",", storageIds);
    }

    private static List<String> storageIds(String field) {
        return field.equals("-") ? List.of() : List.of(field.split(","));
    }

    private static long readAcceptedEpoch(Path directory) {
        String stored = DurableFiles.read(acceptedEpochFile(directory));
        return stored != null ? Long.parseLong(stored) : -1;
    }

    // Beside the shard's directory, so a promise made without a copy survives the copy's creation
    private static Path acceptedEpochFile(Path directory) {
        return directory.resolveSibling(directory.getFileName() + ".accepted-epoch");
    }
}
