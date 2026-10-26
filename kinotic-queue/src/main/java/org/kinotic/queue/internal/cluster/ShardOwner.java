package org.kinotic.queue.internal.cluster;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.internal.cluster.message.AppendRecord;
import org.kinotic.queue.internal.cluster.message.AppendRequest;
import org.kinotic.queue.internal.cluster.message.FetchResponse;
import org.kinotic.queue.internal.cluster.message.LeaseRequest;
import org.kinotic.queue.internal.cluster.message.LeaseResponse;
import org.kinotic.queue.internal.cluster.message.SettleRequest;
import org.kinotic.queue.internal.cluster.message.ShardId;
import org.kinotic.queue.internal.log.BatchSlot;
import org.kinotic.queue.internal.log.Membership;
import org.kinotic.queue.internal.log.QueueLog;
import org.kinotic.queue.internal.log.RecordSlot;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardLog;
import org.kinotic.queue.internal.log.StaleEpochException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

/**
 * Runs a shard placed on this node as its owner. While another node still owns the shard and this node's copy is far
 * behind it, the owner waits for its copy to catch up as that node's follower, and requests reach that node through
 * this one. On start the owner takes an epoch newer than any the shard has seen, has every reachable copy promise to
 * refuse older owners, brings its copy level with the most advanced of them, and writes a marker opening its epoch. It
 * then appends records, replicates them to the followers and to every other copy counting toward a commit, and treats
 * records as committed once a majority of the shard's voting copies holds them and the marker. Only committed records
 * are acknowledged to appenders and served to consumers. An owner whose shard is placed elsewhere keeps serving it
 * until the node placed as its owner takes it. Runs on the queue node's context.
 */
@Slf4j
@Accessors(fluent = true)
final class ShardOwner {

    /**
     * How long a fetch for records that are not yet committed is held before it is answered with none.
     */
    static final long FETCH_WAIT_MS = 1_000;

    /**
     * How many bytes of records one batch read for a fetch or for replication carries at most.
     */
    static final long MAX_BATCH_BYTES = 4 * 1024 * 1024;

    /**
     * How long an append, or a change of the shard's offsets, waits for a majority of the shard's copies before it fails.
     */
    static final long COMMIT_TIMEOUT_MS = 5_000;

    private static final long RECOVERY_RETRY_MS = 1_000;
    // An owner no majority of the shard's copies answered for this long steps down, so a cut-off owner stops leasing
    // records and failing appends, and an owner its copies reach takes over
    private static final long MAJORITY_SILENCE_MS = 10_000;
    private static final long SWEEP_INTERVAL_MS = 250;
    // A producer's last batch is forgotten after this long without a request; the producer has given up on it by then
    private static final long PRODUCER_IDLE_MS = 10 * 60_000;
    // How much of the shard's end a new owner reads to learn the batches producers may still be sending again
    private static final long PRODUCER_SCAN_BYTES = 64 * 1024 * 1024;
    private static final int PRODUCER_SCAN_CHUNK = 1_024;
    private static final long STATE_SAVE_INTERVAL_MS = 1_000;

    private final Vertx vertx;
    private final ShardPlacement placement;
    private final QueueClusterClient client;
    private final ShardStateRepository shardStates;
    private final QueueMetrics metrics;
    private final ShardTakeover takeover;
    private final ShardId shardId;
    private final QueueLog queueLog;
    private final ShardLog shardLog;
    private final Map<String, ShardReplicator> replicators = new HashMap<>();
    private final TreeMap<Long, PendingMajority> pendingAppends = new TreeMap<>();
    // Keyed by the QueueLog.positionsVersion a majority of copies must hold
    private final TreeMap<Long, PendingMajority> pendingOffsetReplications = new TreeMap<>();
    private final List<PendingFetch> pendingFetches = new ArrayList<>();
    private final Map<String, Future<WorkDispatcher>> dispatchers = new HashMap<>();
    private final Map<Long, ProducerBatch> producerBatches = new HashMap<>();
    // Appends a record to a group's dead-letter queue, by group name
    private final BiFunction<String, ShardEntry, Future<Void>> deadLetters;
    // The storage id of this node's copy
    private final String storageId;

    // The storage id of each follower's node, by node id
    private Map<String, String> followers;
    // The nodes replicated to, with the storage id of each one's copy: the followers, and the nodes of the other copies
    // counting toward a commit, which a change of placement can leave outside the followers
    private Map<String, String> targets = Map.of();
    private ShardMembership membership;
    /**
     * The node serving the shard while this node's copy catches up, or null when this node serves it or no node does.
     */
    @Getter
    private String actingOwner;
    private long epoch;
    private long epochStartOffset;
    /**
     * The offset before which every record of the shard is committed.
     */
    @Getter
    private long committedOffset;
    private String recoveryFailure = "it has not taken ownership yet";
    /**
     * Whether this node serves the shard.
     */
    @Getter
    private boolean active;
    @Getter
    private boolean stopped;
    private boolean stateSaveScheduled;
    private boolean deleting;
    private long sweepTimer = -1;
    // Completes once the batches found at the shard's end are known, before which no batch is appended
    private Future<Void> producersRecovered = Future.succeededFuture();

    ShardOwner(Vertx vertx,
               ShardPlacement placement,
               QueueClusterClient client,
               ShardStateRepository shardStates,
               QueueMetrics metrics,
               ShardAssignment assignment,
               BiFunction<String, ShardEntry, Future<Void>> deadLetters) {
        this.vertx = vertx;
        this.placement = placement;
        this.client = client;
        this.shardStates = shardStates;
        this.metrics = metrics;
        this.shardId = assignment.shardId();
        this.queueLog = assignment.queueLog();
        this.shardLog = assignment.shardLog();
        this.followers = assignment.followers();
        this.deadLetters = deadLetters;
        this.storageId = placement.storageId(placement.localNodeId());
        this.takeover = new ShardTakeover(vertx, placement, client, shardStates, shardId, queueLog, shardLog);
    }

    void start() {
        recover();
    }

    /**
     * Appends a batch of records. A batch its producer already sent is not written again: the records of it the shard
     * holds keep their offsets, and only the ones it lacks are written.
     *
     * @return the offset of each record, once every record of the batch is committed
     */
    Future<List<Long>> append(AppendRequest request) {
        if (followers.size() + 1 < placement.quorum()) {
            return Future.failedFuture(QueueFailure.NO_QUORUM.exception("Only " + (followers.size() + 1) + " copies of " + shardId
                                                                                + " are placed, " + placement.quorum() + " are needed"));
        }
        if (!active) {
            return Future.failedFuture(recovering());
        }
        return producersRecovered.compose(v -> stopped ? Future.failedFuture(changedOwner(shardId)) : appendBatch(request));
    }

    private Future<List<Long>> appendBatch(AppendRequest request) {
        ProducerBatch last = producerBatches.get(request.producerId());
        if (last != null && request.sequence() < last.sequence()) {
            return Future.failedFuture(new IllegalArgumentException("Batch " + request.sequence() + " was followed by batch "
                                                                            + last.sequence() + " from the same producer"));
        }
        boolean resent = last != null && request.sequence() == last.sequence();
        List<Long> none = missing(request.records().size());
        // A failed write of the batch appended none of its records
        Future<List<Long>> written = resent ? last.written().otherwise(none).compose(offsets -> writeMissing(request, offsets))
                                            : writeMissing(request, none);
        producerBatches.put(request.producerId(), new ProducerBatch(request.sequence(), written, MonotonicClock.millis()));
        long received = System.nanoTime();
        return written.compose(this::awaitCommitted).onSuccess(offsets -> {
            // A batch sent again was counted when first written
            if (!resent) {
                metrics.recordAppend(shardId.queue(), offsets.size(), Duration.ofNanos(System.nanoTime() - received));
            }
        });
    }

    // Writes the batch's records the shard does not hold yet, the ones at -1 in offsets
    private Future<List<Long>> writeMissing(AppendRequest request, List<Long> offsets) {
        // A batch sent again after this owner stopped would be written twice when the first write succeeded after it
        // stopped, and the shard's next owner recognizes the batch either way
        if (stopped) {
            return Future.failedFuture(changedOwner(shardId));
        }
        List<ShardEntry> records = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < offsets.size(); i++) {
            if (offsets.get(i) < 0) {
                AppendRecord record = request.records().get(i);
                BatchSlot slot = new BatchSlot(request.producerId(), request.sequence(), i, offsets.size());
                records.add(ShardEntry.record(-1, epoch, record.key(), record.payload(), slot));
                indexes.add(i);
            }
        }
        Future<List<Long>> ret;
        if (records.isEmpty()) {
            ret = Future.succeededFuture(offsets);
        } else {
            long appendEpoch = epoch;
            ret = vertx.executeBlocking(() -> shardLog.append(appendEpoch, records), false).transform(ar -> {
                Future<List<Long>> written;
                if (ar.failed()) {
                    // A failed append fences this epoch on the shard, so this owner can append nothing more. The append
                    // wrote none of the records, so the client sends them again to the shard's next owner.
                    if (!(ar.cause() instanceof StaleEpochException)) {
                        log.warn("Appending to {} failed, stepping down", shardId, ar.cause());
                    }
                    stepDown();
                    written = Future.failedFuture(changedOwner(shardId));
                } else if (stopped) {
                    // The records may still be committed by the next owner, which then recognizes the batch
                    written = Future.failedFuture(changedOwner(shardId));
                } else {
                    List<Long> merged = new ArrayList<>(offsets);
                    for (int i = 0; i < indexes.size(); i++) {
                        merged.set(indexes.get(i), ar.result().get(i));
                    }
                    replicators.values().forEach(ShardReplicator::notifyChanged);
                    updateCommittedOffset();
                    written = Future.succeededFuture(merged);
                }
                return written;
            });
        }
        return ret;
    }

    private Future<List<Long>> awaitCommitted(List<Long> offsets) {
        long last = offsets.stream().mapToLong(Long::longValue).max().orElseThrow();
        Future<List<Long>> ret;
        if (last < committedOffset) {
            ret = Future.succeededFuture(offsets);
        } else {
            ret = awaitMajority(pendingAppends, last).map(offsets);
        }
        return ret;
    }

    private static List<Long> missing(int count) {
        return new ArrayList<>(Collections.nCopies(count, -1L));
    }

    // The last batch of each producer among the entries at the shard's end
    private Map<Long, ProducerBatch> scanProducerBatches() {
        Map<Long, Long> sequences = new HashMap<>();
        Map<Long, List<Long>> offsets = new HashMap<>();
        long scannedBytes = 0;
        long start = shardLog.start().offset();
        long to = shardLog.nextOffset();
        while (to > start && scannedBytes < PRODUCER_SCAN_BYTES) {
            long from = Math.max(start, to - PRODUCER_SCAN_CHUNK);
            List<RecordSlot> chunk = shardLog.readSlots(from, (int) (to - from));
            to = from;
            for (RecordSlot entry : chunk) {
                scannedBytes += entry.size();
                BatchSlot slot = entry.slot();
                if (slot != null && slot.sequence() >= sequences.getOrDefault(slot.producerId(), -1L)) {
                    if (slot.sequence() > sequences.getOrDefault(slot.producerId(), -1L)) {
                        sequences.put(slot.producerId(), slot.sequence());
                        offsets.put(slot.producerId(), missing(slot.size()));
                    }
                    offsets.get(slot.producerId()).set(slot.index(), entry.offset());
                }
            }
        }
        long now = MonotonicClock.millis();
        Map<Long, ProducerBatch> ret = new HashMap<>();
        sequences.forEach((producerId, sequence) -> ret.put(producerId, new ProducerBatch(sequence, Future.succeededFuture(offsets.get(producerId)), now)));
        return ret;
    }

    /**
     * @return the offset the shard's next record will be committed at
     */
    Future<Long> findCommittedOffset() {
        Future<Long> ret;
        if (!active) {
            ret = Future.failedFuture(recovering());
        } else if (!epochCommitted()) {
            ret = Future.failedFuture(noCommittedOffset());
        } else {
            ret = Future.succeededFuture(committedOffset);
        }
        return ret;
    }

    /**
     * Returns committed entries starting at {@code offset}, holding the request up to {@link #FETCH_WAIT_MS} while
     * none is committed there yet.
     *
     * @param maxBytes the size after which no further entry is returned; the first entry is returned however large
     */
    Future<FetchResponse> fetch(long offset, int max, long maxBytes) {
        if (!active) {
            return Future.failedFuture(recovering());
        }
        Future<FetchResponse> ret;
        if (offset < committedOffset) {
            ret = readCommitted(offset, max, maxBytes);
        } else {
            Promise<FetchResponse> promise = Promise.promise();
            long timerId = vertx.setTimer(FETCH_WAIT_MS, t -> {
                if (pendingFetches.removeIf(pending -> pending.promise() == promise)) {
                    promise.complete(new FetchResponse(List.of(), offset));
                }
            });
            pendingFetches.add(new PendingFetch(offset, max, maxBytes, promise, timerId));
            ret = promise.future();
        }
        return ret;
    }

    /**
     * Leases committed records to a worker of a group, opening the group's dispatcher on the first request. A request
     * for no records is answered once the dispatcher is open.
     */
    Future<LeaseResponse> lease(LeaseRequest request) {
        if (!active) {
            return Future.failedFuture(recovering());
        }
        Future<WorkDispatcher> dispatcher = dispatchers.get(request.groupName());
        // A latest start is the committed offset, which is only known once this epoch's marker is committed
        if (dispatcher == null && request.startPosition() == StartPosition.LATEST && !epochCommitted()) {
            return Future.failedFuture(noCommittedOffset());
        }
        if (dispatcher == null) {
            String groupName = request.groupName();
            dispatcher = new WorkDispatcher(vertx, shardId, groupName, shardLog, queueLog.groupOffsets(), () -> committedOffset,
                                            this::replicateOffsets, entry -> deadLetters.apply(groupName, entry))
                    .open(request.startPosition());
            dispatchers.put(request.groupName(), dispatcher);
            // A failed open is dropped, so the group's next request opens the dispatcher again
            Future<WorkDispatcher> opening = dispatcher;
            opening.onFailure(e -> dispatchers.remove(request.groupName(), opening));
        }
        return dispatcher.compose(opened -> leaseFrom(opened, request));
    }

    private Future<LeaseResponse> leaseFrom(WorkDispatcher dispatcher, LeaseRequest request) {
        Future<LeaseResponse> ret;
        if (stopped) {
            ret = Future.failedFuture(changedOwner(shardId));
        } else if (request.max() == 0) {
            ret = Future.succeededFuture(new LeaseResponse(List.of()));
        } else {
            ret = dispatcher.lease(request.workerId(), request.max(), request.leaseMillis())
                            .onSuccess(response -> {
                                int redelivered = (int) response.leased().stream().filter(leased -> leased.deliveryCount() > 1).count();
                                if (redelivered > 0) {
                                    metrics.recordRedeliveries(shardId.queue(), request.groupName(), redelivered);
                                }
                            });
        }
        return ret;
    }

    /**
     * Applies what a worker did with a record leased to it.
     */
    Future<Void> settle(SettleRequest request) {
        Future<WorkDispatcher> dispatcher = dispatchers.get(request.groupName());
        if (!active || dispatcher == null) {
            return Future.failedFuture(QueueFailure.LEASE_EXPIRED.exception("Offset " + request.offset() + " of " + shardId
                                                                                    + " is not leased to this worker"));
        }
        return dispatcher.compose(opened -> opened.settle(request.workerId(), request.settlement(), request.offset()));
    }

    /**
     * Copies the shard's consumer and group offsets to its followers.
     *
     * @return completes once a majority of the shard's copies holds every offset this copy holds now
     */
    Future<Void> replicateOffsets() {
        Future<Void> ret;
        if (active) {
            ret = awaitMajority(pendingOffsetReplications, queueLog.positionsVersion(shardId.shard()));
            replicators.values().forEach(ShardReplicator::notifyChanged);
            completeReplicatedOffsets();
        } else {
            ret = Future.failedFuture(changedOwner(shardId));
        }
        return ret;
    }

    void updateFollowers(Map<String, String> followers) {
        if (!this.followers.equals(followers)) {
            this.followers = followers;
            if (active) {
                updateTargets();
                updateCommittedOffset();
            }
        }
    }

    /**
     * @return how many of this copy's entries forced to disk each node holding a copy lacks, by node id
     */
    Map<String, Long> replicaLag() {
        Map<String, Long> ret = new HashMap<>();
        long own = shardLog.syncedOffset();
        replicators.forEach((node, replicator) -> ret.put(node, Math.max(0, own - replicator.matchedOffset())));
        return ret;
    }

    void stop() {
        if (!stopped) {
            stopped = true;
            active = false;
            replicators.values().forEach(ShardReplicator::stop);
            replicators.clear();
            QueueFailureException notOwner = changedOwner(shardId);
            if (sweepTimer >= 0) {
                vertx.cancelTimer(sweepTimer);
            }
            failAll(pendingAppends, notOwner);
            failAll(pendingOffsetReplications, notOwner);
            pendingFetches.forEach(pending -> {
                vertx.cancelTimer(pending.timerId());
                pending.promise().tryFail(notOwner);
            });
            pendingFetches.clear();
            dispatchers.values().forEach(dispatcher -> dispatcher.onSuccess(WorkDispatcher::stop));
            dispatchers.clear();
        }
    }

    // Stops owning the shard while it is still placed here; the queue node starts a new owner, which recovers it again
    private void stepDown() {
        if (!stopped) {
            metrics.recordStepDown(shardId.queue());
        }
        stop();
    }

    private void recover() {
        long proposed = takeover.proposeEpoch();
        takeover.findActingOwner()
                .compose(acting -> {
                    actingOwner = acting;
                    return acting != null ? Future.failedFuture("node " + acting + " serves the shard while this copy catches up")
                                          : takeover.takeOwnership(proposed);
                })
                .onComplete(ar -> {
                    if (!stopped) {
                        if (ar.succeeded()) {
                            activate(proposed, ar.result());
                        } else {
                            retryRecovery(ar.cause().getMessage());
                        }
                    }
                });
    }

    // Starts serving the shard at the epoch, whose marker is at the offset
    private void activate(long takenEpoch, long markerOffset) {
        epoch = takenEpoch;
        epochStartOffset = markerOffset;
        active = true;
        metrics.recordTakeover(shardId.queue());
        membership = new ShardMembership(placement, shardLog, this::appendMembership, this::copies);
        sweepTimer = vertx.setPeriodic(SWEEP_INTERVAL_MS, t -> sweep());
        producersRecovered = vertx.executeBlocking(this::scanProducerBatches, false)
                                  .recover(e -> {
                                      log.warn("Reading the producers' last batches of {} failed; a batch sent again before the owner"
                                                       + " changed may be written twice", shardId, e);
                                      return Future.succeededFuture(Map.of());
                                  })
                                  .onSuccess(producerBatches::putAll)
                                  .mapEmpty();
        updateTargets();
        updateCommittedOffset();
        log.info("Owning {} at epoch {} from offset {}", shardId, epoch, epochStartOffset);
    }

    private void retryRecovery(String failure) {
        recoveryFailure = failure;
        if (actingOwner != null) {
            log.debug("{} waits to take ownership: {}", shardId, recoveryFailure);
        } else {
            log.warn("Recovering {} failed, retrying: {}", shardId, recoveryFailure);
        }
        vertx.setTimer(RECOVERY_RETRY_MS, t -> {
            if (!stopped) {
                recover();
            }
        });
    }

    // Appends a membership change as this owner and replicates it like a record
    private Future<Long> appendMembership(Membership change) {
        long appendEpoch = epoch;
        return vertx.executeBlocking(() -> shardLog.appendMembership(appendEpoch, change), false).transform(ar -> {
            Future<Long> ret;
            if (ar.failed()) {
                if (ar.cause() instanceof StaleEpochException) {
                    stepDown();
                }
                ret = Future.failedFuture(ar.cause());
            } else if (stopped) {
                ret = Future.failedFuture(changedOwner(shardId));
            } else {
                updateTargets();
                replicators.values().forEach(ShardReplicator::notifyChanged);
                updateCommittedOffset();
                ret = Future.succeededFuture(ar.result());
            }
            return ret;
        });
    }

    // The storage ids of this copy and the followers' copies
    private List<String> copies() {
        List<String> ret = new ArrayList<>();
        ret.add(storageId);
        ret.addAll(followers.values());
        return ret;
    }

    // How far the copy with the storage id holds the shard, measured by the given progress of a follower
    private long progress(String copy, long ownProgress, ToLongFunction<ShardReplicator> followerProgress) {
        long ret = 0;
        if (copy.equals(storageId)) {
            ret = ownProgress;
        } else {
            for (Map.Entry<String, String> target : targets.entrySet()) {
                ShardReplicator replicator = replicators.get(target.getKey());
                if (target.getValue().equals(copy) && replicator != null) {
                    ret = followerProgress.applyAsLong(replicator);
                }
            }
        }
        return ret;
    }

    // Replicates to the followers and to the live nodes of every other copy counting toward a commit
    private void updateTargets() {
        Map<String, String> updated = new LinkedHashMap<>(followers);
        ShardEntry latest = shardLog.latestMembership();
        if (latest != null) {
            List<String> counting = new ArrayList<>(latest.membership().voting());
            counting.addAll(latest.membership().joining());
            for (String copy : counting) {
                String node = copy.equals(storageId) ? null : placement.nodeWithStorageId(copy);
                if (node != null) {
                    updated.putIfAbsent(node, copy);
                }
            }
        }
        if (!updated.equals(targets)) {
            targets = updated;
            replicators.keySet().removeIf(node -> {
                boolean removed = !updated.containsKey(node);
                if (removed) {
                    replicators.get(node).stop();
                }
                return removed;
            });
            updated.keySet().forEach(this::startReplicator);
        }
    }

    private void startReplicator(String follower) {
        replicators.computeIfAbsent(follower, node -> {
            ShardReplicator replicator = new ShardReplicator(vertx, client, node, shardId, queueLog, shardLog, epoch,
                                                             this::onReplicatorProgress, this::stepDown);
            replicator.start();
            return replicator;
        });
    }

    private void onReplicatorProgress() {
        updateCommittedOffset();
        completeReplicatedOffsets();
    }

    // The committed offset only moves once a majority holds this epoch's marker: until then a record of an earlier epoch
    // held by a majority could still be replaced by an owner that never saw it.
    private void updateCommittedOffset() {
        if (stepDownIfFenced()) {
            return;
        }
        // This copy's entries count toward a commit once forced to disk, as a follower's do once it acknowledges them
        long ownOffset = shardLog.syncedOffset();
        long majorityOffset = membership.committable(copy -> progress(copy, ownOffset, ShardReplicator::matchedOffset));
        if (majorityOffset > epochStartOffset && majorityOffset > committedOffset) {
            committedOffset = majorityOffset;
            completeThrough(pendingAppends, committedOffset - 1);
            answerPendingFetches();
            scheduleStateSave();
            // A dispatcher still opening holds no lease requests yet
            dispatchers.values().stream().filter(Future::succeeded).forEach(dispatcher -> dispatcher.result().onCommitted());
        }
        membership.advance(committedOffset, copy -> progress(copy, ownOffset, ShardReplicator::matchedOffset));
    }

    // Fetches waiting at the same offset, usually consumers caught up with the shard, share one read
    private void answerPendingFetches() {
        Map<Long, List<PendingFetch>> answerable = new HashMap<>();
        Iterator<PendingFetch> iterator = pendingFetches.iterator();
        while (iterator.hasNext()) {
            PendingFetch pending = iterator.next();
            if (pending.from() < committedOffset) {
                iterator.remove();
                vertx.cancelTimer(pending.timerId());
                answerable.computeIfAbsent(pending.from(), from -> new ArrayList<>()).add(pending);
            }
        }
        answerable.forEach((from, fetches) -> {
            int max = fetches.stream().mapToInt(PendingFetch::max).max().orElseThrow();
            long maxBytes = fetches.stream().mapToLong(PendingFetch::maxBytes).max().orElseThrow();
            readCommitted(from, max, maxBytes).onComplete(ar -> fetches.forEach(pending -> {
                if (ar.succeeded()) {
                    pending.promise().complete(ar.result().limit(pending.max(), Math.min(pending.maxBytes(), MAX_BATCH_BYTES)));
                } else {
                    pending.promise().fail(ar.cause());
                }
            }));
        });
    }

    // A newer owner reached this copy, so the copies this owner counts may already hold that owner's entries
    private boolean stepDownIfFenced() {
        boolean ret = shardLog.acceptedEpoch() > epoch;
        if (ret) {
            log.info("{} promised an owner newer than epoch {}, stepping down", shardId, epoch);
            stepDown();
        }
        return ret;
    }

    // Completes once a majority of copies confirms what it is keyed by, fails once COMMIT_TIMEOUT_MS passes
    private static Future<Void> awaitMajority(TreeMap<Long, PendingMajority> pending, long key) {
        return pending.computeIfAbsent(key, k -> new PendingMajority(Promise.promise(), MonotonicClock.millis() + COMMIT_TIMEOUT_MS))
                      .promise()
                      .future();
    }

    // Polled one at a time, since a completion's callbacks may add requests
    private static void completeThrough(TreeMap<Long, PendingMajority> pending, long lastConfirmed) {
        while (!pending.isEmpty() && pending.firstKey() <= lastConfirmed) {
            pending.pollFirstEntry().getValue().promise().complete();
        }
    }

    private static void failAll(TreeMap<Long, PendingMajority> pending, Throwable cause) {
        while (!pending.isEmpty()) {
            pending.pollFirstEntry().getValue().promise().tryFail(cause);
        }
    }

    private static void failExpired(TreeMap<Long, PendingMajority> pending, long now, LongFunction<String> describe) {
        pending.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().deadline() <= now;
            if (expired) {
                entry.getValue().promise().fail(QueueFailure.COMMIT_TIMEOUT.exception(describe.apply(entry.getKey())
                                                                                              + " was not confirmed by a majority of copies"));
            }
            return expired;
        });
    }

    // Fails the requests whose majority did not confirm them in time, and drops dispatchers of groups gone quiet
    private void sweep() {
        if (stepDownIfFenced()) {
            return;
        }
        // A copy counting toward a commit may come back on a node that joined after the last change of followers
        updateTargets();
        long now = MonotonicClock.millis();
        if (membership.committable(copy -> progress(copy, now, ShardReplicator::lastAnsweredMillis)) < now - MAJORITY_SILENCE_MS) {
            log.warn("No majority of the copies of {} answered for {} ms, stepping down", shardId, MAJORITY_SILENCE_MS);
            stepDown();
        } else {
            replicators.values().forEach(ShardReplicator::heartbeat);
            failExpired(pendingAppends, now, offset -> "Offset " + offset + " of " + shardId);
            failExpired(pendingOffsetReplications, now, version -> "A change of the positions on " + shardId);
            producerBatches.values().removeIf(batch -> batch.written().isComplete() && batch.lastUsed() < now - PRODUCER_IDLE_MS);
            deleteRetained();
            dispatchers.values().removeIf(dispatcher -> {
                boolean idle = dispatcher.succeeded() && dispatcher.result().isIdle(now);
                if (idle) {
                    dispatcher.result().stop();
                }
                return idle;
            });
        }
    }

    // Deletes the committed entries the retention lets go; the followers delete theirs once a batch carries the new start.
    // Waits for the latest membership change to be committed, since the shard keeps only that change once older ones are
    // deleted, and for the scan of the producers' batches, which reads the entries deleted.
    private void deleteRetained() {
        long retainedFrom = Math.min(shardLog.retainedFrom(), committedOffset);
        ShardEntry latest = shardLog.latestMembership();
        if (!deleting
                && producersRecovered.isComplete()
                && (latest == null || latest.offset() < committedOffset)
                && retainedFrom > shardLog.start().offset()) {
            membership.rewriteBefore(retainedFrom);
            deleting = true;
            vertx.executeBlocking(() -> shardLog.deleteBefore(retainedFrom), false).onComplete(ar -> {
                deleting = false;
                if (ar.failed()) {
                    log.warn("Deleting the entries of {} before offset {} failed", shardId, retainedFrom, ar.cause());
                }
            });
        }
    }

    private void completeReplicatedOffsets() {
        completeThrough(pendingOffsetReplications,
                        membership.committable(copy -> progress(copy, Long.MAX_VALUE, ShardReplicator::replicatedPositionsVersion)));
    }

    // Until a majority holds this epoch's marker, the committed offset is not yet the shard's
    private boolean epochCommitted() {
        return committedOffset > epochStartOffset;
    }

    // A fetch from before the shard's start continues at the start, past the entries the retention deleted
    private Future<FetchResponse> readCommitted(long offset, int max, long maxBytes) {
        long committed = committedOffset;
        return vertx.executeBlocking(() -> {
            long from = Math.max(offset, shardLog.start().offset());
            List<ShardEntry> entries = shardLog.read(from, (int) Math.min(max, committed - from), Math.min(maxBytes, MAX_BATCH_BYTES));
            return new FetchResponse(entries, from + entries.size());
        }, false);
    }

    /**
     * @return the failure of a request the shard's owner can no longer serve, since it stopped owning the shard
     */
    static QueueFailureException changedOwner(ShardId shardId) {
        return QueueFailure.NOT_OWNER.exception("The owner of " + shardId + " changed");
    }

    private QueueFailureException recovering() {
        return QueueFailure.NOT_OWNER.exception("The owner of " + shardId + " is recovering: " + recoveryFailure);
    }

    private QueueFailureException noCommittedOffset() {
        return QueueFailure.NOT_OWNER.exception("The owner of " + shardId + " has no committed offset yet in this epoch");
    }

    // Records the committed offset for the shard's next owner at most once per interval
    private void scheduleStateSave() {
        if (!stateSaveScheduled) {
            stateSaveScheduled = true;
            vertx.setTimer(STATE_SAVE_INTERVAL_MS, t -> {
                stateSaveScheduled = false;
                if (!stopped) {
                    long committed = committedOffset;
                    vertx.executeBlocking(() -> {
                        shardStates.saveCommittedOffset(shardId, committed);
                        return null;
                    }, false).onFailure(e -> log.warn("Saving the committed offset of {} failed", shardId, e));
                }
            });
        }
    }
}
