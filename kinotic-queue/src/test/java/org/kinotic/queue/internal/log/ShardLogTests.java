package org.kinotic.queue.internal.log;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        try (ShardLog follower = new ShardLog(shardDirectory)) {
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
        try (ShardLog reopened = new ShardLog(shardDirectory)) {
            assertEquals(6, reopened.nextOffset());
            assertEquals(3, reopened.lastEpoch());
        }
    }

    @Test
    public void aMismatchSendsTheOwnerBackToTheStartOfTheDifferingEpochAndChangesNothing() {
        try (ShardLog follower = new ShardLog(directory.resolve("0"))) {
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
        try (ShardLog follower = new ShardLog(directory.resolve("0"))) {
            appendAll(follower, 1, 0, 5);

            // The owner holds only 0-2 of epoch 1
            ReplicationResult result = follower.replicate(4, 2, 1, 3, List.of());

            assertEquals(ReplicationStatus.ACCEPTED, result.status());
            assertEquals(3, follower.nextOffset());
        }
    }

    @Test
    public void aDelayedBatchArrivingAfterALaterOneKeepsTheOwnersNewerEntries() {
        try (ShardLog follower = new ShardLog(directory.resolve("0"))) {
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
        try (ShardLog shard = new ShardLog(shardDirectory)) {
            appendAll(shard, 1, 0, 2);
            // Each promise answers with the epoch promised before it
            assertEquals(1, shard.promise(5));
            assertEquals(5, shard.promise(4));
        }
        try (ShardLog reopened = new ShardLog(shardDirectory)) {
            assertEquals(5, reopened.acceptedEpoch());
            assertEquals(ReplicationStatus.STALE_EPOCH, reopened.replicate(4, 1, 1, 3, List.of(entry(2, 4))).status());
            assertThrows(StaleEpochException.class, () -> reopened.append(4, "key", new byte[0]));
            assertEquals(2, reopened.nextOffset());
        }
    }

    @Test
    public void aTruncationInterruptedAfterMovingTheShardAsideCompletesOnOpen() throws Exception {
        Path shardDirectory = directory.resolve("0");
        Path copy = directory.resolve("0.truncating");
        Path replaced = directory.resolve("0.replaced");
        try (ShardLog shard = new ShardLog(shardDirectory)) {
            appendAll(shard, 1, 0, 5);
        }
        try (ShardLog truncated = new ShardLog(copy)) {
            appendAll(truncated, 1, 0, 3);
        }
        // The state a crash leaves between moving the shard aside and moving the copy into its place
        Files.move(shardDirectory, replaced);

        assertTrue(ShardLog.exists(shardDirectory));
        try (ShardLog reopened = new ShardLog(shardDirectory)) {
            assertEquals(3, reopened.nextOffset());
        }
        assertFalse(Files.exists(copy));
        assertFalse(Files.exists(replaced));
    }

    @Test
    public void aTruncationInterruptedBeforeTheShardWasMovedIsDiscardedOnOpen() throws Exception {
        Path shardDirectory = directory.resolve("0");
        Path copy = directory.resolve("0.truncating");
        try (ShardLog shard = new ShardLog(shardDirectory)) {
            appendAll(shard, 1, 0, 5);
        }
        // A partial copy, as a crash during the copy leaves it
        FileUtils.writeStringToFile(copy.resolve("partial").toFile(), "partial", StandardCharsets.UTF_8);

        try (ShardLog reopened = new ShardLog(shardDirectory)) {
            assertEquals(5, reopened.nextOffset());
        }
        assertFalse(Files.exists(copy));
    }

    @Test
    public void markersAreStoredWithoutAKeyAndReadsStopAtTheByteBudget() {
        try (ShardLog shard = new ShardLog(directory.resolve("0"))) {
            assertEquals(0, shard.appendMarker(1));
            shard.append(1, "a", new byte[600]);
            shard.append(1, "b", new byte[600]);
            shard.append(1, "c", new byte[600]);

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
            assertEquals(firstOffset + i, shard.append(epoch, "key-" + (firstOffset + i), new byte[]{(byte) epoch}));
        }
    }

    private static ShardEntry entry(long offset, long epoch) {
        return new ShardEntry(offset, epoch, "key-" + offset, new byte[]{(byte) epoch});
    }
}
