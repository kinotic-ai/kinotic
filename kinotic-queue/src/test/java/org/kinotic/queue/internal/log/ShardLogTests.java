package org.kinotic.queue.internal.log;

import net.openhft.chronicle.core.time.SetTimeProvider;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.DocumentContext;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import org.kinotic.queue.api.config.QueueProperties;
import org.kinotic.queue.api.model.QueueDefinition;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the rules a shard copy applies to its owner's batches, against real Chronicle Queue files: copies agree up to
 * the last entry with the same offset and epoch, differing entries are replaced without losing the entries before them,
 * and promises outlive restarts.
 */
public class ShardLogTests {

    private static final QueueProperties PROPERTIES = new QueueProperties();

    @TempDir
    private Path directory;

    @Test
    public void entriesThatDifferFromTheOwnersAreReplacedAndEarlierOnesKept() {
        Path shardDirectory = directory.resolve("0");
        try (ShardLog follower = new ShardLog(shardDirectory, PROPERTIES)) {
            // Offsets 0-2 came from the owner of epoch 1, offsets 3-4 from an owner of epoch 2 that never committed them
            appendAll(follower, 1, 0, 3);
            appendAll(follower, 2, 3, 2);

            // The owner of epoch 3 holds 0-2 from epoch 1 and wrote 3-5 itself
            ReplicationResult result = follower.replicate(new ReplicationBatch(3, 2, 1, 0, 6, List.of(entry(3, 3), entry(4, 3), entry(5, 3))));

            assertEquals(ReplicationStatus.ACCEPTED, result.status());
            assertEquals(6, result.nextOffset());
            assertEquals(6, follower.nextOffset());
            List<ShardEntry> entries = follower.read(0, 10, Long.MAX_VALUE);
            assertEquals(List.of(1L, 1L, 1L, 3L, 3L, 3L), entries.stream().map(ShardEntry::epoch).toList());
            assertEquals("key-3", entries.get(3).key());
        }
        // The replaced entries stay replaced after the shard is reopened
        try (ShardLog reopened = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(6, reopened.nextOffset());
            assertEquals(3, reopened.lastEpoch());
        }
    }

    @Test
    public void aMismatchSendsTheOwnerBackToTheStartOfTheDifferingEpochAndChangesNothing() {
        try (ShardLog follower = new ShardLog(directory.resolve("0"), PROPERTIES)) {
            appendAll(follower, 1, 0, 3);
            appendAll(follower, 2, 3, 4);

            // The owner's entry 6 is from epoch 5, so the follower's whole epoch 2 run starting at 3 is suspect
            ReplicationResult result = follower.replicate(new ReplicationBatch(6, 6, 5, 0, 9, List.of(entry(7, 6), entry(8, 6))));

            assertEquals(ReplicationStatus.MISMATCH, result.status());
            assertEquals(3, result.nextOffset());
            assertEquals(7, follower.nextOffset());
            assertEquals(2, follower.lastEpoch());
        }
    }

    @Test
    public void entriesPastTheOwnersEndAreRemoved() {
        try (ShardLog follower = new ShardLog(directory.resolve("0"), PROPERTIES)) {
            appendAll(follower, 1, 0, 5);

            // The owner holds only 0-2 of epoch 1
            ReplicationResult result = follower.replicate(new ReplicationBatch(4, 2, 1, 0, 3, List.of()));

            assertEquals(ReplicationStatus.ACCEPTED, result.status());
            assertEquals(3, follower.nextOffset());
        }
    }

    @Test
    public void aDelayedBatchArrivingAfterALaterOneKeepsTheOwnersNewerEntries() {
        try (ShardLog follower = new ShardLog(directory.resolve("0"), PROPERTIES)) {
            // The owner of epoch 2 held 0-2 when it read the first batch and 0-4 when it read the second
            List<ShardEntry> firstBatch = List.of(entry(0, 2), entry(1, 2), entry(2, 2));
            List<ShardEntry> secondBatch = List.of(entry(0, 2), entry(1, 2), entry(2, 2), entry(3, 2), entry(4, 2));
            follower.replicate(new ReplicationBatch(2, -1, -1, 0, 5, secondBatch));

            ReplicationResult result = follower.replicate(new ReplicationBatch(2, -1, -1, 0, 3, firstBatch));

            assertEquals(ReplicationStatus.ACCEPTED, result.status());
            assertEquals(5, follower.nextOffset());
        }
    }

    @Test
    public void aBatchFromAnOwnerOlderThanThePromisedOneIsRefusedAfterARestart() {
        Path shardDirectory = directory.resolve("0");
        try (ShardLog shard = new ShardLog(shardDirectory, PROPERTIES)) {
            appendAll(shard, 1, 0, 2);
            // Each promise answers with the epoch promised before it
            assertEquals(1, shard.promise(5));
            assertEquals(5, shard.promise(4));
        }
        try (ShardLog reopened = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(5, reopened.acceptedEpoch());
            assertEquals(ReplicationStatus.STALE_EPOCH, reopened.replicate(new ReplicationBatch(4, 1, 1, 0, 3, List.of(entry(2, 4)))).status());
            assertThrows(StaleEpochException.class, () -> append(reopened, 4, "key", new byte[0]));
            assertEquals(2, reopened.nextOffset());
        }
    }

    @Test
    public void truncationsStackSegmentsThatReadsSpanAndAReopenKeeps() {
        Path shardDirectory = directory.resolve("0");
        try (ShardLog follower = new ShardLog(shardDirectory, PROPERTIES)) {
            appendAll(follower, 1, 0, 5);
            // The owner of epoch 2 replaces 3-4 with its own 3-5
            follower.replicate(new ReplicationBatch(2, 2, 1, 0, 6, List.of(entry(3, 2), entry(4, 2), entry(5, 2))));
            // The owner of epoch 3 keeps 3 of epoch 2 and replaces the rest with its own 4-6
            follower.replicate(new ReplicationBatch(3, 3, 2, 0, 7, List.of(entry(4, 3), entry(5, 3), entry(6, 3))));

            assertEquals(List.of(1L, 1L, 1L, 2L, 3L, 3L, 3L), epochs(follower.read(0, 10, Long.MAX_VALUE)));
            // A read crossing from one segment into the next
            assertEquals(List.of(2L, 3L, 4L), follower.read(2, 3, Long.MAX_VALUE).stream().map(ShardEntry::offset).toList());
        }
        try (ShardLog reopened = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(7, reopened.nextOffset());
            assertEquals(3, reopened.lastEpoch());
            assertEquals(List.of(1L, 1L, 1L, 2L, 3L, 3L, 3L), epochs(reopened.read(0, 10, Long.MAX_VALUE)));
            append(reopened, 3, "key-7", new byte[]{3});
            assertEquals(8, reopened.nextOffset());
        }
    }

    @Test
    public void aBatchIsAppendedInOrderAndKeepsItsSlotsAfterAReopen() {
        Path shardDirectory = directory.resolve("0");
        List<ShardEntry> batch = List.of(ShardEntry.record(-1, 1, "a", new byte[]{1}, new BatchSlot(7, 3, 0, 2)),
                                         ShardEntry.record(-1, 1, "b", new byte[]{2}, new BatchSlot(7, 3, 1, 2)));
        try (ShardLog shard = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(0, shard.appendMarker(1));

            assertEquals(List.of(1L, 2L), shard.append(1, batch));
        }
        try (ShardLog reopened = new ShardLog(shardDirectory, PROPERTIES)) {
            List<ShardEntry> entries = reopened.read(1, 10, Long.MAX_VALUE);
            assertEquals(List.of("a", "b"), entries.stream().map(ShardEntry::key).toList());
            assertEquals(List.of(new BatchSlot(7, 3, 0, 2), new BatchSlot(7, 3, 1, 2)), entries.stream().map(ShardEntry::slot).toList());
            assertNull(reopened.read(0, 1, Long.MAX_VALUE).getFirst().slot());
        }
    }

    @Test
    public void theLastMembershipChangeSurvivesAReopenAndATruncationRemovesTheOnesItCuts() {
        Path shardDirectory = directory.resolve("0");
        Membership first = new Membership(List.of("a", "b", "c"), List.of());
        Membership joint = new Membership(List.of("a", "b", "c"), List.of("a", "b", "d"));
        try (ShardLog follower = new ShardLog(shardDirectory, PROPERTIES)) {
            assertNull(follower.latestMembership());
            follower.replicate(new ReplicationBatch(1, -1, -1, 0, 3, List.of(ShardEntry.marker(0, 1), ShardEntry.membership(1, 1, first), entry(2, 1))));
            assertEquals(first, follower.latestMembership().membership());
            // The owner of epoch 2 holds 0-2 and adds a joint change at 3, which epoch 3 later replaces
            follower.replicate(new ReplicationBatch(2, 2, 1, 0, 4, List.of(ShardEntry.membership(3, 2, joint))));
            assertEquals(joint, follower.latestMembership().membership());
            follower.replicate(new ReplicationBatch(3, 2, 1, 0, 4, List.of(ShardEntry.marker(3, 3))));

            assertEquals(1, follower.latestMembership().offset());
            assertEquals(first, follower.latestMembership().membership());
        }
        try (ShardLog reopened = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(first, reopened.latestMembership().membership());
            assertEquals(first, reopened.read(1, 1, Long.MAX_VALUE).getFirst().membership());
        }
    }

    @Test
    public void aMembershipListedPastTheShardsEndByACrashIsDroppedOnOpen() throws Exception {
        Path shardDirectory = directory.resolve("0");
        try (ShardLog shard = new ShardLog(shardDirectory, PROPERTIES)) {
            shard.appendMarker(1);
            shard.appendMembership(1, new Membership(List.of("a"), List.of()));
        }
        // The listing a crash leaves after listing a change at offset 2 and before writing it
        Files.writeString(shardDirectory.resolve("memberships"), "1 1 a -\n2 1 a,b -");

        try (ShardLog reopened = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(1, reopened.latestMembership().offset());
            assertEquals(2, reopened.nextOffset());
        }
    }

    @Test
    public void aMembershipListedForAnOffsetHoldingAnotherEntryIsDroppedOnOpen() throws Exception {
        Path shardDirectory = directory.resolve("0");
        try (ShardLog shard = new ShardLog(shardDirectory, PROPERTIES)) {
            shard.appendMarker(1);
            shard.appendMembership(1, new Membership(List.of("a"), List.of()));
            appendAll(shard, 1, 2, 2);
        }
        // The listing a failed write of a change at offset 2 leaves, after a record took the offset
        Files.writeString(shardDirectory.resolve("memberships"), "1 1 a -\n2 1 a,b -");

        try (ShardLog reopened = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(1, reopened.latestMembership().offset());
        }
        try (ShardLog reopenedAgain = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(1, reopenedAgain.latestMembership().offset());
        }
    }

    @Test
    public void aSegmentSealedPastTheEntriesACrashKeptEndsAtThemAndLaterSegmentsAreDropped() throws Exception {
        Path shardDirectory = directory.resolve("0");
        try (ShardLog shard = new ShardLog(shardDirectory, PROPERTIES)) {
            appendAll(shard, 1, 0, 5);
        }
        String segment = Files.readAllLines(shardDirectory.resolve("segments")).get(1).split(" ")[0];
        // The manifest a crash leaves when entries 5-7 were sealed into the segment and 8 on written to another one,
        // and none of them reached the disk
        long created = System.currentTimeMillis();
        Files.writeString(shardDirectory.resolve("segments"), "start 0 -1\n" + segment + " 0 8 " + created + " 0\n8-lost 8 -1 " + created + " 0");

        try (ShardLog reopened = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(5, reopened.nextOffset());
            assertEquals(1, reopened.lastEpoch());
            appendAll(reopened, 2, 5, 2);
            assertEquals(List.of(1L, 1L, 1L, 1L, 1L, 2L, 2L), epochs(reopened.read(0, 10, Long.MAX_VALUE)));
        }
        try (ShardLog reopenedAgain = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(7, reopenedAgain.nextOffset());
            assertEquals(List.of(1L, 1L, 1L, 1L, 1L, 2L, 2L), epochs(reopenedAgain.read(0, 10, Long.MAX_VALUE)));
        }
        assertFalse(Files.exists(shardDirectory.resolve("8-lost")));
    }

    @Test
    public void aClockSetBackAcrossADayKeepsAppendingAfterTheNewestEntries() throws Exception {
        Path shardDirectory = directory.resolve("0");
        Path segmentDirectory = shardDirectory.resolve("0-ahead");
        // A segment written while the clock was a day ahead, in the layout ShardSegment writes: offset, epoch, type
        SetTimeProvider aDayAhead = new SetTimeProvider();
        aDayAhead.currentTimeMillis(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
        try (SingleChronicleQueue queue = SingleChronicleQueueBuilder.binary(segmentDirectory)
                                                                     .rollCycle(RollCycles.FAST_DAILY)
                                                                     .timeProvider(aDayAhead)
                                                                     .build();
             ExcerptAppender appender = queue.createAppender()) {
            for (long offset = 0; offset < 3; offset++) {
                try (DocumentContext dc = appender.writingDocument()) {
                    dc.wire().bytes().writeLong(offset).writeLong(1).writeByte((byte) 1);
                }
            }
        }
        Files.writeString(shardDirectory.resolve("segments"), "start 0 -1\n0-ahead 0 -1 " + System.currentTimeMillis() + " 0");

        try (ShardLog shard = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(3, shard.nextOffset());
            shard.appendMarker(2);
            shard.appendMarker(2);
            assertEquals(List.of(1L, 1L, 1L, 2L, 2L), epochs(shard.read(0, 10, Long.MAX_VALUE)));
        }
        try (ShardLog reopened = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(5, reopened.nextOffset());
            assertEquals(List.of(1L, 1L, 1L, 2L, 2L), epochs(reopened.read(0, 10, Long.MAX_VALUE)));
        }
    }

    @Test
    public void aFailedAppendFencesItsEpochSoOnlyANewerOwnerWritesItsOffsetsAgain() {
        try (ShardLog shard = new ShardLog(directory.resolve("0"), PROPERTIES)) {
            appendAll(shard, 1, 0, 2);
            // A record without a payload fails partway through the batch
            List<ShardEntry> failing = List.of(ShardEntry.record(-1, 1, "key-2", new byte[]{1}, new BatchSlot(1, 1, 0, 2)),
                                               ShardEntry.record(-1, 1, "key-3", null, new BatchSlot(1, 1, 1, 2)));
            assertThrows(RuntimeException.class, () -> shard.append(1, failing));
            assertEquals(2, shard.nextOffset());

            assertThrows(StaleEpochException.class, () -> append(shard, 1, "key-2", new byte[]{1}));
            assertEquals(2, append(shard, 2, "key-2", new byte[]{2}));
            assertEquals(List.of(1L, 1L, 2L), epochs(shard.read(0, 10, Long.MAX_VALUE)));
        }
    }

    @Test
    public void aSegmentDirectoryACrashLeftUnlistedIsDeletedOnOpen() throws Exception {
        Path shardDirectory = directory.resolve("0");
        try (ShardLog shard = new ShardLog(shardDirectory, PROPERTIES)) {
            appendAll(shard, 1, 0, 5);
        }
        // The directory of a segment created after a crash interrupted its listing, or deleted before it was removed
        Path stray = shardDirectory.resolve("3-stray");
        FileUtils.writeStringToFile(stray.resolve("partial").toFile(), "partial", StandardCharsets.UTF_8);

        try (ShardLog reopened = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(5, reopened.nextOffset());
            assertEquals(5, reopened.read(0, 10, Long.MAX_VALUE).size());
        }
        assertFalse(Files.exists(stray));
    }

    @Test
    public void aPromiseMadeWithoutACopyHoldsForTheCopyCreatedLaterAndAfterARestart() {
        QueueDefinition definition = new QueueDefinition("orders", 2);
        try (QueueLog queueLog = QueueLog.openOrCreate(directory.resolve("orders"), definition, PROPERTIES)) {
            assertEquals(-1, queueLog.promise(1, 7));
            assertEquals(7, queueLog.promise(1, 5));
            assertNull(queueLog.findShard(1));
        }
        try (QueueLog reopened = QueueLog.openOrCreate(directory.resolve("orders"), definition, PROPERTIES)) {
            ShardLog copy = reopened.shard(1);
            assertEquals(7, copy.acceptedEpoch());
            assertEquals(ReplicationStatus.STALE_EPOCH, copy.replicate(new ReplicationBatch(6, -1, -1, 0, 1, List.of(entry(0, 6)))).status());
            assertEquals(0, copy.nextOffset());
        }
    }

    @Test
    public void markersAreStoredWithoutAKeyAndReadsStopAtTheByteBudget() {
        try (ShardLog shard = new ShardLog(directory.resolve("0"), PROPERTIES)) {
            assertEquals(0, shard.appendMarker(1));
            append(shard, 1, "a", new byte[600]);
            append(shard, 1, "b", new byte[600]);
            append(shard, 1, "c", new byte[600]);

            List<ShardEntry> entries = shard.read(0, 10, 1_000);

            assertEquals(3, entries.size());
            assertFalse(entries.getFirst().isRecord());
            assertNull(entries.getFirst().key());
            assertArrayEquals(new byte[600], entries.get(1).payload());
            // The first entry is returned however large it is
            assertEquals(1, shard.read(1, 10, 1).size());
        }
    }

    @Test
    public void segmentsCloseWithAgeAndWholeSegmentsOlderThanTheRetentionPeriodAreDeleted() throws Exception {
        Path shardDirectory = directory.resolve("0");
        QueueProperties twoSeconds = new QueueProperties().setRetentionPeriod(Duration.ofSeconds(2));
        try (ShardLog shard = new ShardLog(shardDirectory, twoSeconds)) {
            appendAll(shard, 1, 0, 3);
            // Segments close at the first write once half the retention period old
            Thread.sleep(1_100);
            appendAll(shard, 1, 3, 3);
            assertEquals(0, shard.retainedFrom());
            Thread.sleep(2_100);
            appendAll(shard, 2, 6, 1);

            // The first segment's entries are all older than the period; the second's are not yet
            assertEquals(3, shard.retainedFrom());
            assertEquals(new LogStart(3, 1), shard.deleteBefore(shard.retainedFrom()));
            assertThrows(IllegalArgumentException.class, () -> shard.read(2, 10, Long.MAX_VALUE));
            assertEquals(List.of(1L, 1L, 1L, 2L), epochs(shard.read(3, 10, Long.MAX_VALUE)));
        }
        try (ShardLog reopened = new ShardLog(shardDirectory, twoSeconds)) {
            assertEquals(new LogStart(3, 1), reopened.start());
            assertEquals(7, reopened.nextOffset());
            assertEquals(List.of(1L, 1L, 1L, 2L), epochs(reopened.read(3, 10, Long.MAX_VALUE)));
        }
    }

    @Test
    public void theOldestSegmentsBeyondTheRetentionSizeAreDeleted() {
        // Segments close at a quarter of the size, so each 100,000-byte record gets its own
        QueueProperties fourHundredKilobytes = new QueueProperties().setRetentionBytes(DataSize.ofBytes(400_000));
        try (ShardLog shard = new ShardLog(directory.resolve("0"), fourHundredKilobytes)) {
            for (int i = 0; i < 10; i++) {
                shard.append(1, List.of(ShardEntry.record(-1, 1, "", new byte[100_000], new BatchSlot(1, i, 0, 1))));
            }

            // Deleting another segment would leave less than 400,000 bytes
            assertEquals(6, shard.retainedFrom());
            shard.deleteBefore(6);
            assertEquals(4, shard.read(6, 10, Long.MAX_VALUE).size());
        }
    }

    @Test
    public void deletingNeverDeletesTheLatestMembershipChange() throws Exception {
        QueueProperties twoSeconds = new QueueProperties().setRetentionPeriod(Duration.ofSeconds(2));
        try (ShardLog shard = new ShardLog(directory.resolve("0"), twoSeconds)) {
            shard.appendMarker(1);
            shard.appendMembership(1, new Membership(List.of("a"), List.of()));
            Thread.sleep(1_100);
            appendAll(shard, 1, 2, 2);
            Thread.sleep(2_100);
            appendAll(shard, 1, 4, 1);
            assertEquals(2, shard.retainedFrom());

            assertEquals(0, shard.deleteBefore(2).offset());
            // Written again, the change no longer holds the old segments back
            shard.appendMembership(1, new Membership(List.of("a"), List.of()));
            assertEquals(2, shard.deleteBefore(2).offset());
            assertEquals(5, shard.latestMembership().offset());
        }
    }

    @Test
    public void aCopyEndingBeforeTheOwnersStartStartsOverThereAndKeepsItAfterAReopen() {
        Path shardDirectory = directory.resolve("0");
        try (ShardLog copy = new ShardLog(shardDirectory, PROPERTIES)) {
            appendAll(copy, 1, 0, 3);

            // The owner deleted everything before offset 100, the last deleted entry being of epoch 4
            ReplicationResult result = copy.replicate(new ReplicationBatch(5, 99, 4, 100, 102, List.of(entry(100, 5), entry(101, 5))));

            assertEquals(ReplicationStatus.ACCEPTED, result.status());
            assertEquals(new LogStart(100, 4), copy.start());
            assertEquals(List.of(5L, 5L), epochs(copy.read(100, 10, Long.MAX_VALUE)));
        }
        try (ShardLog reopened = new ShardLog(shardDirectory, PROPERTIES)) {
            assertEquals(new LogStart(100, 4), reopened.start());
            assertEquals(102, reopened.nextOffset());
            // A batch continuing from the deleted entry before the start is checked against its epoch
            assertEquals(ReplicationStatus.ACCEPTED,
                         reopened.replicate(new ReplicationBatch(5, 101, 5, 100, 103, List.of(entry(102, 5)))).status());
        }
    }

    @Test
    public void aCopyThatDiffersFromTheOwnerAtItsStartStartsOverThere() {
        try (ShardLog copy = new ShardLog(directory.resolve("0"), PROPERTIES)) {
            appendAll(copy, 1, 0, 3);
            appendAll(copy, 2, 3, 3);

            // The owner's entries from offset 4 on are of epoch 3, and its entry at 3 was of epoch 1
            ReplicationResult result = copy.replicate(new ReplicationBatch(3, 3, 1, 4, 5, List.of(entry(4, 3))));

            assertEquals(ReplicationStatus.ACCEPTED, result.status());
            assertEquals(new LogStart(4, 1), copy.start());
            assertEquals(5, copy.nextOffset());
        }
    }

    @Test
    public void aFollowerDeletesItsSegmentsThatEndByTheOwnersStart() throws Exception {
        QueueProperties twoSeconds = new QueueProperties().setRetentionPeriod(Duration.ofSeconds(2));
        try (ShardLog follower = new ShardLog(directory.resolve("0"), twoSeconds)) {
            follower.replicate(new ReplicationBatch(1, -1, -1, 0, 3, List.of(entry(0, 1), entry(1, 1), entry(2, 1))));
            Thread.sleep(1_100);
            follower.replicate(new ReplicationBatch(1, 2, 1, 0, 6, List.of(entry(3, 1), entry(4, 1), entry(5, 1))));

            // The owner deleted its entries before offset 4, which ends no segment of the follower past offset 3
            follower.replicate(new ReplicationBatch(1, 5, 1, 4, 7, List.of(entry(6, 1))));

            assertEquals(new LogStart(3, 1), follower.start());
        }
    }

    @Test
    public void aCopyHoldingAnOldOwnersTailFromBeforeItsStartIsResentFromTheOwnersLastEntryOfThatEpoch() {
        try (ShardLog owner = new ShardLog(directory.resolve("owner"), PROPERTIES);
             ShardLog copy = new ShardLog(directory.resolve("copy"), PROPERTIES)) {
            // The owner of epoch 2 wrote 0-9, of which 0-5 were committed; the owner of epoch 3 holds 0-5 and wrote 6-8
            appendAll(owner, 2, 0, 6);
            appendAll(owner, 3, 6, 3);
            // The copy deleted 0-2 and holds 3-9 of epoch 2
            copy.replicate(new ReplicationBatch(2, 2, 2, 3, 10, List.of(entry(3, 2), entry(4, 2), entry(5, 2), entry(6, 2), entry(7, 2),
                                                                         entry(8, 2), entry(9, 2))));
            assertEquals(new LogStart(3, 2), copy.start());

            // A batch from before the copy's start is resent from its start
            assertEquals(new ReplicationResult(ReplicationStatus.MISMATCH, 3), copy.replicate(new ReplicationBatch(3, 0, 2, 0, 9, List.of())));
            // The copy's epoch 2 began before its start, which it reports with the epoch
            ReplicationResult mismatch = copy.replicate(new ReplicationBatch(3, 7, 3, 0, 9, List.of(entry(8, 3))));
            assertEquals(new ReplicationResult(ReplicationStatus.MISMATCH, 3, 2), mismatch);
            // The owner's entries of epoch 2 end at 6, which the copy holds the same up to
            assertEquals(6, owner.endOfEpoch(mismatch.conflictEpoch()));
            ReplicationResult resent = copy.replicate(new ReplicationBatch(3, 5, 2, 0, 9, owner.read(6, 10, Long.MAX_VALUE)));

            assertEquals(new ReplicationResult(ReplicationStatus.ACCEPTED, 9), resent);
            assertEquals(List.of(2L, 2L, 2L, 3L, 3L, 3L), epochs(copy.read(3, 10, Long.MAX_VALUE)));
        }
    }

    private static void appendAll(ShardLog shard, long epoch, long firstOffset, int count) {
        for (int i = 0; i < count; i++) {
            assertEquals(firstOffset + i, append(shard, epoch, "key-" + (firstOffset + i), new byte[]{(byte) epoch}));
        }
    }

    private static List<Long> epochs(List<ShardEntry> entries) {
        return entries.stream().map(ShardEntry::epoch).toList();
    }

    private static long append(ShardLog shard, long epoch, String key, byte[] payload) {
        return shard.append(epoch, List.of(ShardEntry.record(-1, epoch, key, payload, new BatchSlot(1, 0, 0, 1)))).getFirst();
    }

    private static ShardEntry entry(long offset, long epoch) {
        return ShardEntry.record(offset, epoch, "key-" + offset, new byte[]{(byte) epoch}, new BatchSlot(1, offset, 0, 1));
    }
}
