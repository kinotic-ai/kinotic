package org.kinotic.queue.internal.cluster;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.internal.cluster.message.FetchRequest;
import org.kinotic.queue.internal.cluster.message.FetchResponse;
import org.kinotic.queue.internal.cluster.message.ShardRequest;
import org.kinotic.queue.internal.cluster.message.ShardStatus;
import org.kinotic.queue.internal.log.ReplicationResult;
import org.kinotic.queue.internal.log.ReplicationStatus;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardLog;
import org.kinotic.queue.internal.log.StaleEpochException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runs a shard placed on this node as its owner. On start the owner recovers: it brings its copy level with the
 * most advanced copy any queue node holds and takes an epoch newer than any the shard has seen. It then appends
 * records, replicates them to the followers, and treats a record as committed once a majority of the replication
 * factor holds it. Only committed records are acknowledged to appenders and served to consumers.
 * Runs on the queue node's context.
 */
@Slf4j
final class ShardOwner {

    /**
     * How long a fetch for records that are not yet committed is held before it is answered with none.
     */
    static final long FETCH_WAIT_MS = 1_000;

    private static final long COMMIT_TIMEOUT_MS = 5_000;
    private static final long RECOVERY_RETRY_MS = 1_000;
    private static final int CATCH_UP_BATCH_SIZE = 1_024;

    private final Vertx vertx;
    private final ShardPlacement placement;
    private final QueueClusterClient client;
    private final String queue;
    private final int shard;
    private final ShardLog shardLog;
    private final Map<String, ShardReplicator> replicators = new HashMap<>();
    private final TreeMap<Long, Promise<Long>> pendingAppends = new TreeMap<>();
    private final List<PendingFetch> pendingFetches = new ArrayList<>();

    private List<String> followers = List.of();
    private long epoch;
    private long committedOffset;
    private boolean active;
    private boolean stopped;

    ShardOwner(Vertx vertx, ShardPlacement placement, QueueClusterClient client, String queue, int shard, ShardLog shardLog) {
        this.vertx = vertx;
        this.placement = placement;
        this.client = client;
        this.queue = queue;
        this.shard = shard;
        this.shardLog = shardLog;
    }

    void start(List<String> followers) {
        this.followers = followers;
        recover();
    }

    /**
     * Appends a record.
     *
     * @return the record's offset, once the record is committed
     */
    Future<Long> append(String key, byte[] payload) {
        if (!active) {
            return Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " is recovering"));
        }
        if (followers.size() + 1 < placement.quorum()) {
            return Future.failedFuture(QueueFailure.NO_QUORUM.exception("Only " + (followers.size() + 1) + " copies of shard " + shard
                                                                                + " of queue " + queue + " are placed, " + placement.quorum() + " are needed"));
        }
        Promise<Long> ret = Promise.promise();
        long appendEpoch = epoch;
        vertx.executeBlocking(() -> shardLog.append(appendEpoch, key, payload), false).onComplete(ar -> {
            if (ar.failed()) {
                if (ar.cause() instanceof StaleEpochException) {
                    stop();
                }
                ret.fail(ar.cause());
            } else if (stopped) {
                // The record may still be committed by the next owner
                ret.fail(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " changed owner"));
            } else {
                long offset = ar.result();
                pendingAppends.put(offset, ret);
                vertx.setTimer(COMMIT_TIMEOUT_MS, t -> {
                    Promise<Long> pending = pendingAppends.remove(offset);
                    if (pending != null) {
                        pending.fail(QueueFailure.COMMIT_TIMEOUT.exception("Offset " + offset + " of shard " + shard + " of queue "
                                                                                   + queue + " was not confirmed by a majority of copies"));
                    }
                });
                replicators.values().forEach(ShardReplicator::notifyAppended);
                updateCommittedOffset();
                // Replication of a later append can commit this offset before this callback runs
                completeCommittedAppends();
            }
        });
        return ret.future();
    }

    /**
     * Returns committed records starting at {@code offset}, holding the request up to {@link #FETCH_WAIT_MS} while
     * none is committed there yet.
     *
     * @param offset the first offset to return, or -1 for the committed offset
     */
    Future<FetchResponse> fetch(long offset, int max) {
        if (!active) {
            return Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " is recovering"));
        }
        long from = offset < 0 ? committedOffset : offset;
        Future<FetchResponse> ret;
        if (from < committedOffset) {
            ret = readCommitted(from, max);
        } else {
            PendingFetch pending = new PendingFetch(from, max, Promise.promise());
            pendingFetches.add(pending);
            vertx.setTimer(FETCH_WAIT_MS, t -> {
                if (pendingFetches.remove(pending)) {
                    pending.promise().complete(new FetchResponse(List.of(), from));
                }
            });
            ret = pending.promise().future();
        }
        return ret;
    }

    void updateFollowers(List<String> followers) {
        if (!this.followers.equals(followers)) {
            this.followers = followers;
            if (active) {
                replicators.keySet().removeIf(node -> {
                    boolean removed = !followers.contains(node);
                    if (removed) {
                        replicators.get(node).stop();
                    }
                    return removed;
                });
                followers.forEach(this::startReplicator);
                updateCommittedOffset();
            }
        }
    }

    boolean isStopped() {
        return stopped;
    }

    void stop() {
        if (!stopped) {
            stopped = true;
            active = false;
            replicators.values().forEach(ShardReplicator::stop);
            replicators.clear();
            QueueFailureException notOwner = QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " changed owner");
            pendingAppends.values().forEach(pending -> pending.tryFail(notOwner));
            pendingAppends.clear();
            pendingFetches.forEach(pending -> pending.promise().tryFail(notOwner));
            pendingFetches.clear();
        }
    }

    private void recover() {
        String self = placement.localNodeId();
        List<String> nodes = placement.queueNodes().stream().filter(node -> !node.equals(self)).toList();
        List<Future<ShardStatus>> statuses = nodes.stream()
                                                  .map(node -> client.status(node, new ShardRequest(queue, shard))
                                                                     .otherwise(ShardStatus.NONE))
                                                  .toList();
        Future.all(statuses).compose(ignored -> {
            ShardStatus own = new ShardStatus(shardLog.nextOffset(), shardLog.lastEpoch(), shardLog.acceptedEpoch());
            long newestEpoch = Math.max(own.lastEpoch(), own.acceptedEpoch());
            String bestNode = null;
            ShardStatus best = own;
            for (int i = 0; i < nodes.size(); i++) {
                ShardStatus status = statuses.get(i).result();
                newestEpoch = Math.max(newestEpoch, Math.max(status.lastEpoch(), status.acceptedEpoch()));
                if (status.isAheadOf(best)) {
                    best = status;
                    bestNode = nodes.get(i);
                }
            }
            long ownerEpoch = Math.max(placement.topologyVersion(), newestEpoch + 1);
            Future<Void> caughtUp = bestNode != null ? catchUp(bestNode, best.nextOffset(), ownerEpoch) : Future.succeededFuture();
            return caughtUp.map(ownerEpoch);
        }).onComplete(ar -> {
            if (!stopped) {
                if (ar.succeeded()) {
                    epoch = ar.result();
                    active = true;
                    followers.forEach(this::startReplicator);
                    updateCommittedOffset();
                    log.info("Owning shard {} of queue {} at epoch {} from offset {}", shard, queue, epoch, shardLog.nextOffset());
                } else {
                    log.warn("Recovering shard {} of queue {} failed, retrying", shard, queue, ar.cause());
                    vertx.setTimer(RECOVERY_RETRY_MS, t -> {
                        if (!stopped) {
                            recover();
                        }
                    });
                }
            }
        });
    }

    // Copies entries from the most advanced copy until this copy reaches its end, emptying this copy first when the
    // two disagree; ShardLog.replicate does the checks exactly as it does for a follower
    private Future<Void> catchUp(String node, long targetNextOffset, long ownerEpoch) {
        long next = shardLog.nextOffset();
        Future<Void> ret;
        if (next >= targetNextOffset) {
            ret = Future.succeededFuture();
        } else {
            ret = client.read(node, new FetchRequest(queue, shard, Math.max(0, next - 1), CATCH_UP_BATCH_SIZE + 1))
                        .compose(response -> vertx.executeBlocking(() -> applyCatchUp(next, response.entries(), targetNextOffset, ownerEpoch), false))
                        .compose(result -> {
                            Future<Void> continued;
                            if (result.status() == ReplicationStatus.STALE_EPOCH) {
                                continued = Future.failedFuture("Shard " + shard + " of queue " + queue + " accepted a newer owner");
                            } else if (result.status() == ReplicationStatus.ACCEPTED && shardLog.nextOffset() == next) {
                                continued = Future.failedFuture("Node " + node + " returned no entries after offset " + next);
                            } else {
                                continued = catchUp(node, targetNextOffset, ownerEpoch);
                            }
                            return continued;
                        });
        }
        return ret;
    }

    private ReplicationResult applyCatchUp(long next, List<ShardEntry> read, long targetNextOffset, long ownerEpoch) {
        ReplicationResult ret;
        if (next > 0 && !read.isEmpty()) {
            ret = shardLog.replicate(ownerEpoch, next - 1, read.getFirst().epoch(), targetNextOffset, read.subList(1, read.size()));
        } else {
            ret = shardLog.replicate(ownerEpoch, -1, -1, targetNextOffset, read);
        }
        return ret;
    }

    private void startReplicator(String follower) {
        replicators.computeIfAbsent(follower, node -> {
            ShardReplicator replicator = new ShardReplicator(vertx, client, node, queue, shard, shardLog, epoch,
                                                             this::updateCommittedOffset, this::stop);
            replicator.start();
            return replicator;
        });
    }

    // The committed offset is the offset a majority of the replication factor has reached; copies that are not placed
    // count as holding nothing
    private void updateCommittedOffset() {
        long[] copies = new long[Math.max(placement.replicationFactor(), followers.size() + 1)];
        copies[0] = shardLog.nextOffset();
        int i = 1;
        for (String follower : followers) {
            ShardReplicator replicator = replicators.get(follower);
            copies[i++] = replicator != null ? replicator.matchedOffset() : 0;
        }
        Arrays.sort(copies);
        long majorityOffset = copies[copies.length - placement.quorum()];
        if (majorityOffset > committedOffset) {
            committedOffset = majorityOffset;
            completeCommittedAppends();
            Iterator<PendingFetch> iterator = pendingFetches.iterator();
            while (iterator.hasNext()) {
                PendingFetch pending = iterator.next();
                if (pending.from() < committedOffset) {
                    iterator.remove();
                    readCommitted(pending.from(), pending.max()).onComplete(pending.promise());
                }
            }
        }
    }

    private void completeCommittedAppends() {
        while (!pendingAppends.isEmpty() && pendingAppends.firstKey() < committedOffset) {
            Map.Entry<Long, Promise<Long>> committed = pendingAppends.pollFirstEntry();
            committed.getValue().complete(committed.getKey());
        }
    }

    private Future<FetchResponse> readCommitted(long from, int max) {
        int count = (int) Math.min(max, committedOffset - from);
        return vertx.executeBlocking(() -> shardLog.read(from, count), false)
                    .map(entries -> new FetchResponse(entries, from + entries.size()));
    }
}
