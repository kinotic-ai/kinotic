package org.kinotic.queue.internal.log;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.queue.api.model.QueueDefinition;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    @TempDir
    private Path directory;

    @Test
    public void entriesThatDifferFromTheOwnersAreReplacedAndEarlierOnesKept() {
        Path shardDirectory = directory.resolve("0");
        try (ShardLog follower = new ShardLog(shardDirectory, true)) {
            // Offsets 0-2 came from the owner of epoch 1, offsets 3-4 from an owner of epoch 2 that never committed them
            appendAll(follower, 1, 0, 3);
            appendAll(follower, 2, 3, 2);

            // The owner of epoch 3 holds 0-2 from epoch 1 and wrote 3-5 itself
            ReplicationResult result = follower.replicate(3, 2, 1, 6, List.of(entry(3, 3), entry(4, 3), entry(5, 3)));

            assertEquals(ReplicationStatus.ACCEPTED, result.status());
            assertEquals(6, result.nextOffset());
            assertEquals(6, follower.nextOffset());
            List<ShardEntry> entries = follower.read(0, 10, Long.MAX_VALUE);
            assertEquals(List.of(1L, 1L, 1L, 3L, 3L, 3L), entries.stream().map(ShardEntry::epoch).toList());
            assertEquals("key-3", entries.get(3).key());
        }
        // The replaced entries stay replaced after the shard is reopened
        try (ShardLog reopened = new ShardLog(shardDirectory, true)) {
            assertEquals(6, reopened.nextOffset());
            assertEquals(3, reopened.lastEpoch());
        }
    }

    @Test
    public void aMismatchSendsTheOwnerBackToTheStartOfTheDifferingEpochAndChangesNothing() {
        try (ShardLog follower = new ShardLog(directory.resolve("0"), true)) {
            appendAll(follower, 1, 0, 3);
            appendAll(follower, 2, 3, 4);

            // The owner's entry 6 is from epoch 5, so the follower's whole epoch 2 run starting at 3 is suspect
            ReplicationResult result = follower.replicate(6, 6, 5, 9, List.of(entry(7, 6), entry(8, 6)));

            assertEquals(ReplicationStatus.MISMATCH, result.status());
            assertEquals(3, result.nextOffset());
            assertEquals(7, follower.nextOffset());
            assertEquals(2, follower.lastEpoch());
        }
    }

    @Test
    public void entriesPastTheOwnersEndAreRemoved() {
        try (ShardLog follower = new ShardLog(directory.resolve("0"), true)) {
            appendAll(follower, 1, 0, 5);

            // The owner holds only 0-2 of epoch 1
            ReplicationResult result = follower.replicate(4, 2, 1, 3, List.of());

            assertEquals(ReplicationStatus.ACCEPTED, result.status());
            assertEquals(3, follower.nextOffset());
        }
    }

    @Test
    public void aDelayedBatchArrivingAfterALaterOneKeepsTheOwnersNewerEntries() {
        try (ShardLog follower = new ShardLog(directory.resolve("0"), true)) {
            // The owner of epoch 2 held 0-2 when it read the first batch and 0-4 when it read the second
            List<ShardEntry> firstBatch = List.of(entry(0, 2), entry(1, 2), entry(2, 2));
            List<ShardEntry> secondBatch = List.of(entry(0, 2), entry(1, 2), entry(2, 2), entry(3, 2), entry(4, 2));
            follower.replicate(2, -1, -1, 5, secondBatch);

            ReplicationResult result = follower.replicate(2, -1, -1, 3, firstBatch);

            assertEquals(ReplicationStatus.ACCEPTED, result.status());
            assertEquals(5, follower.nextOffset());
        }
    }

    @Test
    public void aBatchFromAnOwnerOlderThanThePromisedOneIsRefusedAfterARestart() {
        Path shardDirectory = directory.resolve("0");
        try (ShardLog shard = new ShardLog(shardDirectory, true)) {
            appendAll(shard, 1, 0, 2);
            // Each promise answers with the epoch promised before it
            assertEquals(1, shard.promise(5));
            assertEquals(5, shard.promise(4));
        }
        try (ShardLog reopened = new ShardLog(shardDirectory, true)) {
            assertEquals(5, reopened.acceptedEpoch());
            assertEquals(ReplicationStatus.STALE_EPOCH, reopened.replicate(4, 1, 1, 3, List.of(entry(2, 4))).status());
            assertThrows(StaleEpochException.class, () -> append(reopened, 4, "key", new byte[0]));
            assertEquals(2, reopened.nextOffset());
        }
    }

    @Test
    public void truncationsStackSegmentsThatReadsSpanAndAReopenKeeps() {
        Path shardDirectory = directory.resolve("0");
        try (ShardLog follower = new ShardLog(shardDirectory, true)) {
            appendAll(follower, 1, 0, 5);
            // The owner of epoch 2 replaces 3-4 with its own 3-5
            follower.replicate(2, 2, 1, 6, List.of(entry(3, 2), entry(4, 2), entry(5, 2)));
            // The owner of epoch 3 keeps 3 of epoch 2 and replaces the rest with its own 4-6
            follower.replicate(3, 3, 2, 7, List.of(entry(4, 3), entry(5, 3), entry(6, 3)));

            assertEquals(List.of(1L, 1L, 1L, 2L, 3L, 3L, 3L), epochs(follower.read(0, 10, Long.MAX_VALUE)));
            // A read crossing from one segment into the next
            assertEquals(List.of(2L, 3L, 4L), follower.read(2, 3, Long.MAX_VALUE).stream().map(ShardEntry::offset).toList());
        }
        try (ShardLog reopened = new ShardLog(shardDirectory, true)) {
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
        List<ShardEntry> batch = List.of(new ShardEntry(-1, 1, "a", new byte[]{1}, new BatchSlot(7, 3, 0, 2)),
                                         new ShardEntry(-1, 1, "b", new byte[]{2}, new BatchSlot(7, 3, 1, 2)));
        try (ShardLog shard = new ShardLog(shardDirectory, true)) {
            assertEquals(0, shard.appendMarker(1));

            assertEquals(List.of(1L, 2L), shard.append(1, batch));
        }
        try (ShardLog reopened = new ShardLog(shardDirectory, true)) {
            List<ShardEntry> entries = reopened.read(1, 10, Long.MAX_VALUE);
            assertEquals(List.of("a", "b"), entries.stream().map(ShardEntry::key).toList());
            assertEquals(List.of(new BatchSlot(7, 3, 0, 2), new BatchSlot(7, 3, 1, 2)), entries.stream().map(ShardEntry::slot).toList());
            assertNull(reopened.read(0, 1, Long.MAX_VALUE).getFirst().slot());
        }
    }

    @Test
    public void aSegmentDirectoryACrashLeftUnlistedIsDeletedOnOpen() throws Exception {
        Path shardDirectory = directory.resolve("0");
        try (ShardLog shard = new ShardLog(shardDirectory, true)) {
            appendAll(shard, 1, 0, 5);
        }
        // The directory of a segment created after a crash interrupted its listing, or deleted before it was removed
        Path stray = shardDirectory.resolve("3-stray");
        FileUtils.writeStringToFile(stray.resolve("partial").toFile(), "partial", StandardCharsets.UTF_8);

        try (ShardLog reopened = new ShardLog(shardDirectory, true)) {
            assertEquals(5, reopened.nextOffset());
            assertEquals(5, reopened.read(0, 10, Long.MAX_VALUE).size());
        }
        assertFalse(Files.exists(stray));
    }

    @Test
    public void aPromiseMadeWithoutACopyHoldsForTheCopyCreatedLaterAndAfterARestart() {
        QueueDefinition definition = new QueueDefinition("orders", 2);
        try (QueueLog queueLog = QueueLog.openOrCreate(directory.resolve("orders"), definition, true)) {
            assertEquals(-1, queueLog.promise(1, 7));
            assertEquals(7, queueLog.promise(1, 5));
            assertNull(queueLog.findShard(1));
        }
        try (QueueLog reopened = QueueLog.openOrCreate(directory.resolve("orders"), definition, true)) {
            ShardLog copy = reopened.shard(1);
            assertEquals(7, copy.acceptedEpoch());
            assertEquals(ReplicationStatus.STALE_EPOCH, copy.replicate(6, -1, -1, 1, List.of(entry(0, 6))).status());
            assertEquals(0, copy.nextOffset());
        }
    }

    @Test
    public void markersAreStoredWithoutAKeyAndReadsStopAtTheByteBudget() {
        try (ShardLog shard = new ShardLog(directory.resolve("0"), true)) {
            assertEquals(0, shard.appendMarker(1));
            append(shard, 1, "a", new byte[600]);
            append(shard, 1, "b", new byte[600]);
            append(shard, 1, "c", new byte[600]);

            List<ShardEntry> entries = shard.read(0, 10, 1_000);

            assertEquals(3, entries.size());
            assertTrue(entries.getFirst().isMarker());
            assertNull(entries.getFirst().key());
            assertArrayEquals(new byte[600], entries.get(1).payload());
            // The first entry is returned however large it is
            assertEquals(1, shard.read(1, 10, 1).size());
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
        return shard.append(epoch, List.of(new ShardEntry(-1, epoch, key, payload, new BatchSlot(1, 0, 0, 1)))).getFirst();
    }

    private static ShardEntry entry(long offset, long epoch) {
        return new ShardEntry(offset, epoch, "key-" + offset, new byte[]{(byte) epoch}, new BatchSlot(1, offset, 0, 1));
    }
}
