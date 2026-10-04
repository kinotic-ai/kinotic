package org.kinotic.queue.internal.cluster;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.internal.log.Membership;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardLog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Decides, on a shard's owner, which copies count toward a commit, and moves that set toward the copies placed on the
 * cluster. The set is the last membership change in the owner's copy of the shard, which replicates with the records,
 * so a later owner that adopts the most advanced copy knows the set it must reach. When the placed copies change to a
 * full set of the replication factor, the new set first catches up without a vote, then a change makes both sets count,
 * and once that change is committed a second change leaves the new set alone. A copy that is placed nowhere while the
 * cluster has too few queue nodes keeps its vote, counting as holding nothing, until a placed copy replaces it. Runs on
 * the owner's context.
 */
@Slf4j
final class ShardMembership {

    // How close to the committed offset a majority of the placed copies must be before they vote, so commits never
    // wait long on copies that are still catching up
    private static final long CATCH_UP_LAG = 1_024;

    private final ShardPlacement placement;
    private final ShardLog shardLog;
    private final Function<Membership, Future<Long>> append;

    private List<String> placed = List.of();
    private boolean appending;

    /**
     * @param append appends a membership change to the shard as its owner
     */
    ShardMembership(ShardPlacement placement, ShardLog shardLog, Function<Membership, Future<Long>> append) {
        this.placement = placement;
        this.shardLog = shardLog;
        this.append = append;
    }

    /**
     * Records the copies, by storage id, now placed on the cluster: the owner's and its followers'.
     */
    void place(List<String> storageIds) {
        placed = storageIds;
    }

    /**
     * @param progress how far the copy with a storage id holds the shard; zero for a copy the owner knows nothing of
     * @return the offset a majority of every set counting toward a commit has reached
     */
    long committable(ToLongFunction<String> progress) {
        ShardEntry latest = shardLog.latestMembership();
        long ret;
        if (latest == null) {
            // A shard no owner has written a membership for yet counts its placed copies
            ret = majority(placed, progress);
        } else {
            ret = majority(latest.membership().voting(), progress);
            if (latest.membership().isJoint()) {
                ret = Math.min(ret, majority(latest.membership().joining(), progress));
            }
        }
        return ret;
    }

    /**
     * Appends the next membership change toward the placed copies once their progress allows it.
     */
    void advance(long committedOffset, ToLongFunction<String> progress) {
        if (!appending && storable(placed)) {
            ShardEntry latest = shardLog.latestMembership();
            Membership next = null;
            if (latest == null) {
                next = new Membership(placed, List.of());
            } else if (latest.membership().isJoint()) {
                if (committedOffset > latest.offset()) {
                    next = new Membership(latest.membership().joining(), List.of());
                }
            } else if (placed.size() >= placement.replicationFactor()
                    && !sameCopies(placed, latest.membership().voting())
                    && majority(placed, progress) + CATCH_UP_LAG >= committedOffset) {
                next = new Membership(latest.membership().voting(), placed);
            }
            if (next != null) {
                appending = true;
                Membership change = next;
                append.apply(change).onComplete(ar -> {
                    appending = false;
                    if (ar.succeeded()) {
                        log.info("Shard {} counts copies {} at offset {}", shardLog, change, ar.result());
                    } else {
                        log.warn("Appending membership {} to shard {} failed", change, shardLog, ar.cause());
                    }
                });
            }
        }
    }

    // The progress a majority of the replication factor has reached among the copies, counting copies the set lacks
    // as holding nothing
    private long majority(List<String> storageIds, ToLongFunction<String> progress) {
        long[] copies = new long[Math.max(placement.replicationFactor(), storageIds.size())];
        for (int i = 0; i < storageIds.size(); i++) {
            copies[i] = progress.applyAsLong(storageIds.get(i));
        }
        Arrays.sort(copies);
        return copies[copies.length - placement.quorum()];
    }

    // A placed copy whose node left before its storage id was read can never answer a later owner, so a set holding one
    // never counts; the next placement leaves that node out
    private static boolean storable(List<String> storageIds) {
        return !storageIds.isEmpty() && storageIds.stream().noneMatch(storageId -> storageId.startsWith("?"));
    }

    private static boolean sameCopies(List<String> a, List<String> b) {
        return new HashSet<>(a).equals(new HashSet<>(b));
    }
}
