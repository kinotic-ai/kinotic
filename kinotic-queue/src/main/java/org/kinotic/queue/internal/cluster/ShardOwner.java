package org.kinotic.queue.internal.cluster;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.internal.cluster.message.AppendRecord;
import org.kinotic.queue.internal.cluster.message.AppendRequest;
import org.kinotic.queue.internal.cluster.message.FetchRequest;
import org.kinotic.queue.internal.cluster.message.FetchResponse;
import org.kinotic.queue.internal.cluster.message.LeaseRequest;
import org.kinotic.queue.internal.cluster.message.LeaseResponse;
import org.kinotic.queue.internal.cluster.message.OwnerStatus;
import org.kinotic.queue.internal.cluster.message.SettleRequest;
import org.kinotic.queue.internal.cluster.message.PrepareRequest;
import org.kinotic.queue.internal.cluster.message.ShardStatus;
import org.kinotic.queue.internal.log.BatchSlot;
import org.kinotic.queue.internal.log.ConsumerOffsetRepository;
import org.kinotic.queue.internal.log.LogStart;
import org.kinotic.queue.internal.log.Membership;
import org.kinotic.queue.internal.log.ReplicationBatch;
import org.kinotic.queue.internal.log.ReplicationResult;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardLog;
import org.kinotic.queue.internal.log.StaleEpochException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.ToLongFunction;

/**
 * Runs a shard placed on this node as its owner. While another node still owns the shard and this node's copy is far
 * behind it, the owner waits for its copy to catch up as that node's follower, and requests reach that node through
 * this one. On start the owner takes an epoch newer than any the shard has seen, has every reachable copy promise to
 * refuse older owners, brings its copy level with the most advanced of them, and writes a marker opening its epoch. It
 * then appends records, replicates them to the followers and to every other copy counting toward a commit, and treats
 * records as committed once a majority of the
 * shard's voting copies holds them and the marker. Only committed records are acknowledged to appenders and served to
 * consumers. An owner whose shard is placed elsewhere keeps serving it until the node placed as its owner takes it.
 * Runs on the queue node's context.
 */
@Slf4j
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
    // A group's dispatcher with nothing in flight is dropped after this long without a request; its position is stored
    private static final long DISPATCHER_IDLE_MS = 5 * 60_000;
    // A producer's last batch is forgotten after this long without a request; the producer has given up on it by then
    private static final long PRODUCER_IDLE_MS = 10 * 60_000;
    // How much of the shard's end a new owner reads to learn the batches producers may still be sending again
    private static final long PRODUCER_SCAN_BYTES = 64 * 1024 * 1024;
    private static final int PRODUCER_SCAN_CHUNK = 1_024;
    private static final long STATE_SAVE_INTERVAL_MS = 1_000;
    private static final int CATCH_UP_BATCH_SIZE = 1_024;
    // An epoch is a generation in its high bits and the owner's node order in its low bits, so no two owners ever
    // share an epoch, and a later generation is always the newer epoch
    private static final int NODE_ORDER_BITS = 24;
    private static final long NODE_ORDER_MASK = (1L << NODE_ORDER_BITS) - 1;

    private final Vertx vertx;
    private final ShardPlacement placement;
    private final QueueClusterClient client;
    private final ShardStateRepository shardStates;
    private final String queue;
    private final int shard;
    private final ShardLog shardLog;
    private final ConsumerOffsetRepository consumerOffsets;
    private final ConsumerOffsetRepository groupOffsets;
    private final Map<String, ShardReplicator> replicators = new HashMap<>();
    private final TreeMap<Long, PendingMajority<Long>> pendingAppends = new TreeMap<>();
    // Keyed by the ShardReplicator.offsetsVersion a majority of copies must hold
    private final TreeMap<Long, PendingMajority<Void>> pendingOffsetReplications = new TreeMap<>();
    private final List<PendingFetch> pendingFetches = new ArrayList<>();
    private final Map<String, Future<WorkDispatcher>> dispatchers = new HashMap<>();
    private final Map<Long, ProducerBatch> producerBatches = new HashMap<>();
    // Appends a record to a group's dead-letter queue, by group name
    private final BiFunction<String, ShardEntry, Future<Void>> deadLetters;
    // The storage id of this node's copy
    private final String storageId;

    // The storage id of each follower's node, by node id
    private Map<String, String> followers = Map.of();
    // The nodes replicated to, with the storage id of each one's copy: the followers, and the nodes of the other copies
    // counting toward a commit, which a change of placement can leave outside the followers
    private Map<String, String> targets = Map.of();
    private ShardMembership membership;
    // The node serving the shard while this node's copy catches up
    private String actingOwner;
    private long newestSeenEpoch;
    private long epoch;
    private long epochStartOffset;
    private long committedOffset;
    private String recoveryFailure = "it has not taken ownership yet";
    private boolean active;
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
               ShardAssignment assignment,
               BiFunction<String, ShardEntry, Future<Void>> deadLetters) {
        this.vertx = vertx;
        this.placement = placement;
        this.client = client;
        this.shardStates = shardStates;
        this.queue = assignment.queue();
        this.shard = assignment.shard();
        this.shardLog = assignment.shardLog();
        this.consumerOffsets = assignment.consumerOffsets();
        this.groupOffsets = assignment.groupOffsets();
        this.deadLetters = deadLetters;
        this.storageId = placement.storageId(placement.localNodeId());
        this.newestSeenEpoch = Math.max(shardLog.lastEpoch(), shardLog.acceptedEpoch());
    }

    void start(Map<String, String> followers) {
        this.followers = followers;
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
            return Future.failedFuture(QueueFailure.NO_QUORUM.exception("Only " + (followers.size() + 1) + " copies of shard " + shard
                                                                                + " of queue " + queue + " are placed, " + placement.quorum() + " are needed"));
        }
        if (!active) {
            return Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue
                                                                                + " is recovering: " + recoveryFailure));
        }
        return producersRecovered.compose(v -> stopped ? Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue "
                                                                                                                + queue + " changed owner"))
                                                       : appendBatch(request));
    }

    private Future<List<Long>> appendBatch(AppendRequest request) {
        ProducerBatch last = producerBatches.get(request.producerId());
        Future<List<Long>> written;
        if (last != null && request.sequence() < last.sequence()) {
            written = Future.failedFuture(new IllegalArgumentException("Batch " + request.sequence() + " was followed by batch "
                                                                               + last.sequence() + " from the same producer"));
        } else {
            if (last != null && request.sequence() == last.sequence()) {
                // A failed write of the batch appended none of its records
                written = last.written().transform(ar -> ar.succeeded() ? writeMissing(request, ar.result())
                                                                        : writeMissing(request, missing(request.records().size())));
            } else {
                written = writeMissing(request, missing(request.records().size()));
            }
            producerBatches.put(request.producerId(), new ProducerBatch(request.sequence(), written, System.currentTimeMillis()));
        }
        return written.compose(this::awaitCommitted);
    }

    // Writes the batch's records the shard does not hold yet, the ones at -1 in offsets
    private Future<List<Long>> writeMissing(AppendRequest request, List<Long> offsets) {
        // A batch sent again after this owner stopped would be written twice when the first write succeeded after it
        // stopped, and the shard's next owner recognizes the batch either way
        if (stopped) {
            return Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " changed owner"));
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
                    // A failed append fences this epoch on the shard, so this owner can append nothing more
                    stop();
                    written = Future.failedFuture(ar.cause());
                } else if (stopped) {
                    // The records may still be committed by the next owner, which then recognizes the batch
                    written = Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " changed owner"));
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
            ret = pendingAppends.computeIfAbsent(last, offset -> new PendingMajority<>(Promise.promise(),
                                                                                       System.currentTimeMillis() + COMMIT_TIMEOUT_MS))
                                .promise().future().map(offsets);
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
            List<ShardEntry> chunk = shardLog.read(from, (int) (to - from), Long.MAX_VALUE);
            to = from;
            for (ShardEntry entry : chunk) {
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
        long now = System.currentTimeMillis();
        Map<Long, ProducerBatch> ret = new HashMap<>();
        sequences.forEach((producerId, sequence) -> ret.put(producerId, new ProducerBatch(sequence, Future.succeededFuture(offsets.get(producerId)), now)));
        return ret;
    }

    /**
     * Returns committed entries starting at {@code offset}, holding the request up to {@link #FETCH_WAIT_MS} while
     * none is committed there yet.
     *
     * @param offset   the first offset to return, or -1 to return no entries and the committed offset at once
     * @param maxBytes the size after which no further entry is returned; the first entry is returned however large
     */
    Future<FetchResponse> fetch(long offset, int max, long maxBytes) {
        if (!active) {
            return Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " is recovering"));
        }
        if (offset < 0 && !epochCommitted()) {
            return Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue
                                                                                + " has no committed offset yet in this epoch"));
        }
        Future<FetchResponse> ret;
        if (offset < 0) {
            ret = Future.succeededFuture(new FetchResponse(List.of(), committedOffset));
        } else if (offset < committedOffset) {
            ret = readCommitted(offset, max, maxBytes);
        } else {
            PendingFetch pending = new PendingFetch(offset, max, maxBytes, Promise.promise());
            pendingFetches.add(pending);
            vertx.setTimer(FETCH_WAIT_MS, t -> {
                if (pendingFetches.remove(pending)) {
                    pending.promise().complete(new FetchResponse(List.of(), offset));
                }
            });
            ret = pending.promise().future();
        }
        return ret;
    }

    /**
     * Leases committed records to a worker of a group, opening the group's dispatcher on the first request. A request
     * for no records is answered once the dispatcher is open.
     */
    Future<LeaseResponse> lease(LeaseRequest request) {
        if (!active) {
            return Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " is recovering"));
        }
        Future<WorkDispatcher> dispatcher = dispatchers.get(request.groupName());
        if (dispatcher == null) {
            // A latest start is the committed offset, which is only known once this epoch's marker is committed
            if (request.startPosition() == StartPosition.LATEST && !epochCommitted()) {
                return Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue
                                                                                    + " has no committed offset yet in this epoch"));
            }
            String groupName = request.groupName();
            dispatcher = WorkDispatcher.open(vertx, queue, shard, groupName, shardLog, groupOffsets, () -> committedOffset,
                                             this::replicateOffsets, entry -> deadLetters.apply(groupName, entry), request.startPosition());
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
            ret = Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " changed owner"));
        } else if (request.max() == 0) {
            ret = Future.succeededFuture(new LeaseResponse(List.of()));
        } else {
            ret = dispatcher.lease(request.workerId(), request.max(), request.leaseMillis());
        }
        return ret;
    }

    /**
     * Applies what a worker did with a record leased to it.
     */
    Future<Void> settle(SettleRequest request) {
        Future<WorkDispatcher> dispatcher = dispatchers.get(request.groupName());
        if (!active || dispatcher == null) {
            return Future.failedFuture(QueueFailure.LEASE_EXPIRED.exception("Offset " + request.offset() + " of shard " + shard
                                                                                    + " of queue " + queue + " is not leased to this worker"));
        }
        return dispatcher.compose(opened -> opened.settle(request.workerId(), request.settlement(), request.offset()));
    }

    /**
     * Copies the shard's consumer and group offsets to its followers.
     *
     * @return completes once a majority of the shard's copies holds every offset this copy holds now
     */
    Future<Void> replicateOffsets() {
        if (!active) {
            return Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " is recovering"));
        }
        return vertx.executeBlocking(() -> ShardReplicator.offsetsVersion(consumerOffsets, groupOffsets, shard), false)
                    .compose(version -> {
                        Future<Void> ret;
                        if (stopped) {
                            ret = Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue
                                                                                               + " changed owner"));
                        } else {
                            ret = pendingOffsetReplication(version).future();
                            replicators.values().forEach(ShardReplicator::notifyChanged);
                            completeReplicatedOffsets();
                        }
                        return ret;
                    });
    }

    void updateFollowers(Map<String, String> followers) {
        if (!this.followers.equals(followers)) {
            this.followers = followers;
            if (active) {
                membership.place(copies());
                updateTargets();
                updateCommittedOffset();
            }
        }
    }

    boolean isStopped() {
        return stopped;
    }

    /**
     * @return whether this node serves the shard
     */
    boolean isActive() {
        return active;
    }

    /**
     * @return the offset before which every record of the shard is committed
     */
    long committedOffset() {
        return committedOffset;
    }

    /**
     * @return the node serving the shard while this node's copy catches up, or null when this node serves it or no
     * node does
     */
    String actingOwner() {
        return active ? null : actingOwner;
    }

    void stop() {
        if (!stopped) {
            stopped = true;
            active = false;
            replicators.values().forEach(ShardReplicator::stop);
            replicators.clear();
            QueueFailureException notOwner = QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " changed owner");
            if (sweepTimer >= 0) {
                vertx.cancelTimer(sweepTimer);
            }
            pendingAppends.values().forEach(pending -> pending.promise().tryFail(notOwner));
            pendingAppends.clear();
            pendingOffsetReplications.values().forEach(pending -> pending.promise().tryFail(notOwner));
            pendingOffsetReplications.clear();
            pendingFetches.forEach(pending -> pending.promise().tryFail(notOwner));
            pendingFetches.clear();
            dispatchers.values().forEach(dispatcher -> dispatcher.onSuccess(WorkDispatcher::stop));
            dispatchers.clear();
        }
    }

    private void recover() {
        long proposed = nextEpoch();
        // A retry proposes a newer epoch, since copies that promised this one would refuse it
        newestSeenEpoch = Math.max(newestSeenEpoch, proposed);
        findActingOwner()
             .compose(acting -> {
                 actingOwner = acting;
                 return acting != null ? Future.failedFuture("node " + acting + " serves the shard while this copy catches up")
                                       : vertx.executeBlocking(() -> placement.replicas(queue, shard), false);
             })
             .compose(replicas -> prepare(proposed, replicas))
             .compose(statuses -> takeOwnership(proposed, statuses))
             .onComplete(ar -> {
                 if (!stopped) {
                     if (ar.succeeded()) {
                         epoch = proposed;
                         epochStartOffset = ar.result();
                         active = true;
                         membership = new ShardMembership(placement, shardLog, this::appendMembership);
                         membership.place(copies());
                         sweepTimer = vertx.setPeriodic(SWEEP_INTERVAL_MS, t -> sweep());
                         producersRecovered = vertx.executeBlocking(this::scanProducerBatches, false)
                                                   .recover(e -> {
                                                       log.warn("Reading the producers' last batches of shard {} of queue {} failed;"
                                                                        + " a batch sent again before the owner changed may be written twice",
                                                                shard, queue, e);
                                                       return Future.succeededFuture(Map.of());
                                                   })
                                                   .onSuccess(producerBatches::putAll)
                                                   .mapEmpty();
                         updateTargets();
                         updateCommittedOffset();
                         log.info("Owning shard {} of queue {} at epoch {} from offset {}", shard, queue, epoch, epochStartOffset);
                     } else {
                         recoveryFailure = ar.cause().getMessage();
                         if (actingOwner != null) {
                             log.debug("Shard {} of queue {} waits to take ownership: {}", shard, queue, recoveryFailure);
                         } else {
                             log.warn("Recovering shard {} of queue {} failed, retrying: {}", shard, queue, recoveryFailure);
                         }
                         vertx.setTimer(RECOVERY_RETRY_MS, t -> {
                             if (!stopped) {
                                 recover();
                             }
                         });
                     }
                 }
             });
    }

    private long nextEpoch() {
        long generation = Math.max(placement.topologyVersion(), (newestSeenEpoch >> NODE_ORDER_BITS) + 1);
        return (generation << NODE_ORDER_BITS) | (placement.localNodeOrder() & NODE_ORDER_MASK);
    }

    // The node that serves the shard while this node's copy is more than a catch-up batch behind it, or null when none
    // does: this node then takes the shard and catches up the rest itself
    private Future<String> findActingOwner() {
        String self = placement.localNodeId();
        List<String> others = placement.queueNodes().stream().filter(node -> !node.equals(self)).toList();
        List<Future<OwnerStatus>> replies = others.stream().map(node -> client.status(node, queue, shard)).toList();
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
                    ? vertx.executeBlocking(() -> ShardStatus.of(shardLog, shardLog.promise(proposed),
                                                                 consumerOffsets.findAll(shard), groupOffsets.findAll(shard)), false)
                    : client.prepare(node, new PrepareRequest(queue, shard, proposed)));
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
    private Future<Long> takeOwnership(long proposed, Map<String, ShardStatus> statuses) {
        String self = placement.localNodeId();
        String source = bestCopy(statuses, self);
        ShardStatus best = statuses.get(source);
        long target = best.nextOffset();
        return vertx.executeBlocking(() -> {
                        statuses.values().forEach(status -> {
                            consumerOffsets.saveAll(shard, status.consumerOffsets());
                            groupOffsets.saveAll(shard, status.groupOffsets());
                        });
                        return shardStates.findCommittedOffset(queue, shard);
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
        return client.read(source, new FetchRequest(queue, shard, readFrom, max, MAX_BATCH_BYTES))
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

    // Appends a membership change as this owner and replicates it like a record
    private Future<Long> appendMembership(Membership change) {
        long appendEpoch = epoch;
        return vertx.executeBlocking(() -> shardLog.appendMembership(appendEpoch, change), false).transform(ar -> {
            Future<Long> ret;
            if (ar.failed()) {
                if (ar.cause() instanceof StaleEpochException) {
                    stop();
                }
                ret = Future.failedFuture(ar.cause());
            } else if (stopped) {
                ret = Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " changed owner"));
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
            ShardReplicator replicator = new ShardReplicator(vertx, client, node, queue, shard, shardLog, consumerOffsets, groupOffsets,
                                                             epoch, this::onReplicatorProgress, this::stop);
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
            completeCommittedAppends();
            Iterator<PendingFetch> iterator = pendingFetches.iterator();
            while (iterator.hasNext()) {
                PendingFetch pending = iterator.next();
                if (pending.from() < committedOffset) {
                    iterator.remove();
                    readCommitted(pending.from(), pending.max(), pending.maxBytes()).onComplete(pending.promise());
                }
            }
            scheduleStateSave();
            dispatchers.values().forEach(dispatcher -> dispatcher.onSuccess(WorkDispatcher::onCommitted));
        }
        membership.advance(committedOffset, copy -> progress(copy, ownOffset, ShardReplicator::matchedOffset));
    }

    // A newer owner reached this copy, so the copies this owner counts may already hold that owner's entries
    private boolean stepDownIfFenced() {
        boolean ret = shardLog.acceptedEpoch() > epoch;
        if (ret) {
            log.info("Shard {} of queue {} promised an owner newer than epoch {}, stepping down", shard, queue, epoch);
            stop();
        }
        return ret;
    }

    private Promise<Void> pendingOffsetReplication(long version) {
        return pendingOffsetReplications.computeIfAbsent(version, v -> new PendingMajority<>(Promise.promise(),
                                                                                             System.currentTimeMillis() + COMMIT_TIMEOUT_MS))
                                        .promise();
    }

    // Fails the requests whose majority did not confirm them in time, and drops dispatchers of groups gone quiet
    private void sweep() {
        if (stepDownIfFenced()) {
            return;
        }
        // A copy counting toward a commit may come back on a node that joined after the last change of followers
        updateTargets();
        long now = System.currentTimeMillis();
        if (membership.committable(copy -> progress(copy, now, ShardReplicator::lastAnsweredMillis)) < now - MAJORITY_SILENCE_MS) {
            log.warn("No majority of the copies of shard {} of queue {} answered for {} ms, stepping down", shard, queue, MAJORITY_SILENCE_MS);
            stop();
            return;
        }
        replicators.values().forEach(ShardReplicator::heartbeat);
        pendingAppends.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().deadline() <= now;
            if (expired) {
                entry.getValue().promise().fail(QueueFailure.COMMIT_TIMEOUT.exception("Offset " + entry.getKey() + " of shard " + shard + " of queue "
                                                                                              + queue + " was not confirmed by a majority of copies"));
            }
            return expired;
        });
        pendingOffsetReplications.values().removeIf(pending -> {
            boolean expired = pending.deadline() <= now;
            if (expired) {
                pending.promise().fail(QueueFailure.COMMIT_TIMEOUT.exception("The offsets of shard " + shard + " of queue " + queue
                                                                                     + " were not confirmed by a majority of copies"));
            }
            return expired;
        });
        producerBatches.values().removeIf(batch -> batch.written().isComplete() && batch.lastUsed() < now - PRODUCER_IDLE_MS);
        deleteRetained();
        dispatchers.values().removeIf(dispatcher -> {
            boolean idle = dispatcher.succeeded() && dispatcher.result().isIdleSince(now - DISPATCHER_IDLE_MS);
            if (idle) {
                dispatcher.result().stop();
            }
            return idle;
        });
    }

    // Deletes the committed entries the retention lets go; the followers delete theirs once a batch carries the new start
    private void deleteRetained() {
        long retainedFrom = Math.min(shardLog.retainedFrom(), committedOffset);
        if (!deleting && retainedFrom > shardLog.start().offset()) {
            membership.rewriteBefore(retainedFrom);
            deleting = true;
            vertx.executeBlocking(() -> shardLog.deleteBefore(retainedFrom), false).onComplete(ar -> {
                deleting = false;
                if (ar.failed()) {
                    log.warn("Deleting the entries of shard {} of queue {} before offset {} failed", shard, queue, retainedFrom, ar.cause());
                }
            });
        }
    }

    private void completeReplicatedOffsets() {
        long majorityVersion = membership.committable(copy -> progress(copy, Long.MAX_VALUE, ShardReplicator::replicatedOffsetsVersion));
        while (!pendingOffsetReplications.isEmpty() && pendingOffsetReplications.firstKey() <= majorityVersion) {
            pendingOffsetReplications.pollFirstEntry().getValue().promise().complete();
        }
    }

    // Until a majority holds this epoch's marker, the committed offset is not yet the shard's
    private boolean epochCommitted() {
        return committedOffset > epochStartOffset;
    }

    private void completeCommittedAppends() {
        while (!pendingAppends.isEmpty() && pendingAppends.firstKey() < committedOffset) {
            Map.Entry<Long, PendingMajority<Long>> committed = pendingAppends.pollFirstEntry();
            committed.getValue().promise().complete(committed.getKey());
        }
    }

    // A fetch from before the shard's start continues at the start, past the entries the retention deleted
    private Future<FetchResponse> readCommitted(long offset, int max, long maxBytes) {
        long from = Math.max(offset, shardLog.start().offset());
        int count = (int) Math.min(max, committedOffset - from);
        return vertx.executeBlocking(() -> shardLog.read(from, count, Math.min(maxBytes, MAX_BATCH_BYTES)), false)
                    .map(entries -> new FetchResponse(entries, from + entries.size()));
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
                        shardStates.saveCommittedOffset(queue, shard, committed);
                        return null;
                    }, false).onFailure(e -> log.warn("Saving the committed offset of shard {} of queue {} failed", shard, queue, e));
                }
            });
        }
    }
}
