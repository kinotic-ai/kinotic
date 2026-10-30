package org.kinotic.queue.internal.cluster;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.queue.internal.cluster.message.FetchRequest;
import org.kinotic.queue.internal.cluster.message.OwnerStatus;
import org.kinotic.queue.internal.cluster.message.PrepareRequest;
import org.kinotic.queue.internal.cluster.message.ShardId;
import org.kinotic.queue.internal.cluster.message.ShardStatus;
import org.kinotic.queue.internal.log.LogStart;
import org.kinotic.queue.internal.log.Membership;
import org.kinotic.queue.internal.log.QueueLog;
import org.kinotic.queue.internal.log.ReplicationBatch;
import org.kinotic.queue.internal.log.ReplicationResult;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Takes a shard for the {@link ShardOwner} placed on this node: has every reachable copy promise an epoch newer than
 * any the shard has seen, so they refuse older owners, brings this node's copy level with the most advanced of them,
 * and writes the marker opening the epoch. Runs on the owner's context.
 */
final class ShardTakeover {

    private static final int CATCH_UP_BATCH_SIZE = 1_024;
    // An epoch is a generation in its high bits and the owner's node order in its low bits, so no two owners ever
    // share an epoch, and a later generation is always the newer epoch
    private static final int NODE_ORDER_BITS = 24;
    private static final long NODE_ORDER_MASK = (1L << NODE_ORDER_BITS) - 1;

    private final Vertx vertx;
    private final ShardPlacement placement;
    private final QueueClusterClient client;
    private final ShardStateRepository shardStates;
    private final ShardId shardId;
    private final QueueLog queueLog;
    private final ShardLog shardLog;
    private long newestSeenEpoch;

    ShardTakeover(Vertx vertx,
                  ShardPlacement placement,
                  QueueClusterClient client,
                  ShardStateRepository shardStates,
                  ShardId shardId,
                  QueueLog queueLog,
                  ShardLog shardLog) {
        this.vertx = vertx;
        this.placement = placement;
        this.client = client;
        this.shardStates = shardStates;
        this.shardId = shardId;
        this.queueLog = queueLog;
        this.shardLog = shardLog;
        this.newestSeenEpoch = Math.max(shardLog.lastEpoch(), shardLog.acceptedEpoch());
    }

    /**
     * Takes the shard at the epoch, which {@link #proposeEpoch} proposed.
     *
     * @return the offset of the marker opening the epoch
     */
    Future<Long> takeOwnership(long proposed) {
        return vertx.executeBlocking(() -> placement.replicas(shardId.queue(), shardId.shard()), false)
                    .compose(replicas -> prepare(proposed, replicas))
                    .compose(statuses -> takeFrom(proposed, statuses));
    }

    /**
     * @return an epoch newer than any the shard has seen, which no other node proposes; each call proposes a newer one,
     * since copies that promised an earlier proposal refuse it
     */
    long proposeEpoch() {
        long generation = Math.max(placement.topologyVersion(), (newestSeenEpoch >> NODE_ORDER_BITS) + 1);
        long ret = (generation << NODE_ORDER_BITS) | (placement.localNodeOrder() & NODE_ORDER_MASK);
        newestSeenEpoch = Math.max(newestSeenEpoch, ret);
        return ret;
    }

    /**
     * @return the node that serves the shard while this node's copy is more than a catch-up batch behind it, or null
     * when none does: this node then takes the shard and catches up the rest itself
     */
    Future<String> findActingOwner() {
        String self = placement.localNodeId();
        List<String> others = placement.queueNodes().stream().filter(node -> !node.equals(self)).toList();
        List<Future<OwnerStatus>> replies = others.stream().map(node -> client.status(node, shardId)).toList();
        return Future.join(replies).transform(ignored -> {
            String acting = null;
            long actingCommitted = -1;
            for (int i = 0; i < others.size(); i++) {
                Future<OwnerStatus> reply = replies.get(i);
                if (reply.succeeded() && reply.result().active() && reply.result().committedOffset() > actingCommitted) {
                    acting = others.get(i);
                    actingCommitted = reply.result().committedOffset();
                }
            }
            return Future.succeededFuture(shardLog.nextOffset() + CATCH_UP_BATCH_SIZE < actingCommitted ? acting : null);
        });
    }

    // Asks every queue node to promise the epoch. Succeeds with the status of each node that answered once a majority of
    // the shard's placed copies has promised, and a majority of every set counting toward a commit in the most advanced
    // copy: any majority that committed a record under an older owner includes one of them, and none of them accepts
    // that owner's records any more.
    private Future<Map<String, ShardStatus>> prepare(long proposed, List<String> replicas) {
        String self = placement.localNodeId();
        Map<String, Future<ShardStatus>> replies = new LinkedHashMap<>();
        for (String node : placement.queueNodes()) {
            replies.put(node, node.equals(self)
                    ? vertx.executeBlocking(() -> ShardStatus.prepare(queueLog, shardId.shard(), proposed), false)
                    : client.prepare(node, new PrepareRequest(shardId, proposed)));
        }
        return Future.join(new ArrayList<>(replies.values())).transform(ignored -> {
            Map<String, ShardStatus> statuses = new HashMap<>();
            replies.forEach((node, reply) -> {
                if (reply.succeeded()) {
                    statuses.put(node, reply.result());
                    newestSeenEpoch = Math.max(newestSeenEpoch, Math.max(reply.result().lastEpoch(), reply.result().acceptedEpoch()));
                }
            });
            long prepared = replicas.stream().filter(statuses::containsKey).count();
            List<String> answered = statuses.keySet().stream().map(placement::storageId).toList();
            ShardEntry membership = statuses.isEmpty() ? null : statuses.get(bestCopy(statuses, self)).membership();
            String missing = membership == null ? null : unansweredMajority(membership.membership(), answered);
            Future<Map<String, ShardStatus>> ret;
            // A copy that had already promised the proposed epoch promised it to another owner: after every queue node
            // restarts, node orders repeat, so two owners can propose the same epoch
            if (statuses.values().stream().anyMatch(status -> status.acceptedEpoch() >= proposed)) {
                ret = Future.failedFuture("a copy had already promised epoch " + proposed + " or a newer one");
            } else if (prepared < placement.quorum() || !statuses.containsKey(self)) {
                ret = Future.failedFuture("only " + prepared + " of the shard's copies answered, " + placement.quorum() + " are needed");
            } else if (missing != null) {
                ret = Future.failedFuture("no majority of the copies " + missing + " counting toward a commit answered");
            } else {
                ret = Future.succeededFuture(statuses);
            }
            return ret;
        });
    }

    // The node holding the most advanced copy among the statuses, preferring this node's own copy among equals
    private static String bestCopy(Map<String, ShardStatus> statuses, String self) {
        String ret = statuses.containsKey(self) ? self : statuses.keySet().iterator().next();
        for (Map.Entry<String, ShardStatus> status : statuses.entrySet()) {
            if (status.getValue().isAheadOf(statuses.get(ret))) {
                ret = status.getKey();
            }
        }
        return ret;
    }

    // The set, of the sets counting toward a commit, no majority of which answered; null when a majority of each did
    private String unansweredMajority(Membership membership, List<String> answered) {
        String ret = null;
        for (List<String> copies : List.of(membership.voting(), membership.joining())) {
            if (!copies.isEmpty() && copies.stream().filter(answered::contains).count() < placement.quorum(copies.size())) {
                ret = copies.toString();
            }
        }
        return ret;
    }

    // Brings this copy level with the most advanced prepared copy and writes the epoch's marker, returning its offset
    private Future<Long> takeFrom(long proposed, Map<String, ShardStatus> statuses) {
        String self = placement.localNodeId();
        String source = bestCopy(statuses, self);
        ShardStatus best = statuses.get(source);
        long target = best.nextOffset();
        return vertx.executeBlocking(() -> {
                        statuses.values().forEach(status -> queueLog.savePositions(shardId.shard(), status.positions()));
                        return shardStates.findCommittedOffset(shardId);
                    }, false)
                    .compose(knownCommitted -> {
                        Future<Void> caughtUp;
                        if (target < knownCommitted) {
                            caughtUp = Future.failedFuture("the reachable copies end at offset " + target
                                                                   + ", but records up to offset " + knownCommitted + " were committed");
                        } else if (source.equals(self)) {
                            caughtUp = Future.succeededFuture();
                        } else {
                            caughtUp = catchUp(source, best.start(), Math.min(shardLog.nextOffset(), target), target, proposed);
                        }
                        return caughtUp;
                    })
                    .compose(v -> vertx.executeBlocking(() -> shardLog.appendMarker(proposed), false));
    }

    // Copies entries from the most advanced copy until this copy matches it up to its end. ShardLog.replicate does the
    // checks exactly as it does for a follower, replacing any entries of this copy that differ, and starting this copy
    // over at the source's start when it ends before it.
    private Future<Void> catchUp(String source, LogStart sourceStart, long from, long target, long proposed) {
        long first = Math.max(from, sourceStart.offset());
        // The source's entry before the batch is read too, for its epoch, unless the batch begins at the source's start
        long readFrom = first > sourceStart.offset() ? first - 1 : first;
        int max = (int) Math.min(CATCH_UP_BATCH_SIZE + 1, target - readFrom);
        return client.read(source, new FetchRequest(shardId, readFrom, max, ShardOwner.MAX_BATCH_BYTES))
                     .compose(response -> vertx.executeBlocking(() -> applyCatchUp(sourceStart, first, response.entries(), target, proposed),
                                                                false))
                     .compose(result -> {
                         Future<Void> ret;
                         switch (result.status()) {
                             case STALE_EPOCH -> ret = Future.failedFuture("the shard promised an owner newer than epoch " + proposed);
                             case MISMATCH -> ret = catchUp(source, sourceStart, result.nextOffset(), target, proposed);
                             default -> {
                                 if (result.nextOffset() >= target) {
                                     ret = Future.succeededFuture();
                                 } else if (result.nextOffset() == first) {
                                     ret = Future.failedFuture("node " + source + " returned no entries after offset " + first);
                                 } else {
                                     ret = catchUp(source, sourceStart, result.nextOffset(), target, proposed);
                                 }
                             }
                         }
                         return ret;
                     });
    }

    private ReplicationResult applyCatchUp(LogStart sourceStart, long first, List<ShardEntry> read, long target, long proposed) {
        // Entries a newer owner wrote to the source since it was prepared would come before this owner's marker
        if (read.stream().anyMatch(entry -> entry.epoch() > proposed)) {
            throw new IllegalStateException("node holding the most advanced copy accepted an owner newer than epoch " + proposed);
        }
        long prevEpoch;
        List<ShardEntry> entries;
        if (first == sourceStart.offset()) {
            prevEpoch = sourceStart.epochBefore();
            entries = read;
        } else {
            if (read.isEmpty()) {
                throw new IllegalStateException("The source copy holds no entry at offset " + (first - 1));
            }
            prevEpoch = read.getFirst().epoch();
            entries = read.subList(1, read.size());
        }
        return shardLog.replicate(new ReplicationBatch(proposed, first - 1, prevEpoch, sourceStart.offset(), target, entries));
    }
}
