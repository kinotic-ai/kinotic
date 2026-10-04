package org.kinotic.queue.internal.cluster;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.queue.internal.QueueTestNode;
import org.kinotic.queue.internal.log.Membership;
import org.kinotic.queue.internal.log.ShardLog;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests which copies' progress {@link ShardMembership} counts as committed, for sets of copies smaller than, as large
 * as and larger than the replication factor of 3, against a real shard and a real node's placement.
 */
public class ShardMembershipTests {

    @TempDir
    private static Path nodeDirectory;

    @TempDir
    private Path directory;

    private static QueueTestNode node;

    @BeforeAll
    public static void startNode() throws Exception {
        node = QueueTestNode.start("a", nodeDirectory.resolve("a"), 3);
    }

    @AfterAll
    public static void stopNode() throws Exception {
        node.close();
    }

    @Test
    public void aSetLargerThanTheReplicationFactorNeedsAMajorityOfItself() {
        try (ShardLog shardLog = shardLogCounting(new Membership(List.of("a", "b", "c", "d"), List.of()))) {
            ShardMembership membership = new ShardMembership(node.placement(), shardLog, change -> Future.failedFuture("unused"));

            // Two of four copies can be disjoint from another two, so they never commit
            assertEquals(0, membership.committable(progress(Map.of("a", 100L, "d", 100L))));
            assertEquals(100, membership.committable(progress(Map.of("a", 100L, "b", 100L, "d", 100L))));
        }
    }

    @Test
    public void aSetSmallerThanTheReplicationFactorNeedsAMajorityOfTheReplicationFactor() {
        try (ShardLog shardLog = shardLogCounting(new Membership(List.of("a", "b"), List.of()))) {
            ShardMembership membership = new ShardMembership(node.placement(), shardLog, change -> Future.failedFuture("unused"));

            assertEquals(0, membership.committable(progress(Map.of("a", 100L))));
            assertEquals(100, membership.committable(progress(Map.of("a", 100L, "b", 100L))));
        }
    }

    @Test
    public void aJointChangeNeedsAMajorityOfBothSets() {
        try (ShardLog shardLog = shardLogCounting(new Membership(List.of("a", "b", "c"), List.of("a", "d", "e", "f")))) {
            ShardMembership membership = new ShardMembership(node.placement(), shardLog, change -> Future.failedFuture("unused"));

            // A majority of the old set, but only two of the four new copies
            assertEquals(0, membership.committable(progress(Map.of("a", 100L, "b", 100L, "d", 100L))));
            assertEquals(100, membership.committable(progress(Map.of("a", 100L, "b", 100L, "d", 100L, "e", 100L))));
        }
    }

    private ShardLog shardLogCounting(Membership voting) {
        ShardLog ret = new ShardLog(directory.resolve("0"), true);
        ret.appendMembership(1, voting);
        return ret;
    }

    private static ToLongFunction<String> progress(Map<String, Long> offsets) {
        return storageId -> offsets.getOrDefault(storageId, 0L);
    }
}
