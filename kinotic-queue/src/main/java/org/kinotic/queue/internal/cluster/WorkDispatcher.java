package org.kinotic.queue.internal.cluster;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.internal.cluster.message.LeaseResponse;
import org.kinotic.queue.internal.cluster.message.LeasedEntry;
import org.kinotic.queue.internal.cluster.message.Settlement;
import org.kinotic.queue.internal.log.ConsumerOffsetRepository;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardLog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Shares one shard's committed records between the workers of one group, on the shard's owner. Each record is leased
 * to one worker at a time; a record whose lease expires or is released goes back to the group, and a record rejected,
 * or leased {@link #MAX_DELIVERIES} times without being accepted, is done once it is in the group's dead-letter queue.
 * A lease a closing worker returns unseen does not count. Records the retention deletes before the group finishes them
 * are skipped.
 * The group's low watermark, the offset before which
 * every record is done, is stored in the queue's group offsets and copied to a majority of the shard's copies, so the
 * shard's next owner resumes the group from it. A lease can be renewed while its worker still processes the record.
 * Runs on the owner's context.
 */
@Slf4j
final class WorkDispatcher {

    /**
     * How many times a record is leased before it goes to the group's dead-letter queue.
     */
    static final int MAX_DELIVERIES = 5;

    // Bounds the records past the low watermark, which the owner tracks in memory and redelivers after a change of owner
    private static final int MAX_IN_FLIGHT = 1_000;

    private final Vertx vertx;
    private final String queue;
    private final int shard;
    private final String groupName;
    private final ShardLog shardLog;
    private final ConsumerOffsetRepository groupOffsets;
    private final LongSupplier committedOffset;
    private final Supplier<Future<Void>> replicateOffsets;
    private final Function<ShardEntry, Future<Void>> deadLetter;
    private final TreeMap<Long, Lease> leased = new TreeMap<>();
    // Records waiting to be leased again, with how many times they have been leased
    private final TreeMap<Long, Integer> released = new TreeMap<>();
    // Done records past the low watermark
    private final TreeSet<Long> done = new TreeSet<>();
    private final List<PendingLease> pendingLeases = new ArrayList<>();

    private long lowWatermark;
    private long nextToLease;
    // Saves run one after another, so a later watermark is never overwritten by an earlier one
    private Future<Void> lastSave = Future.succeededFuture();
    private boolean stopped;
    private long lastRequest = System.currentTimeMillis();
    // Records on their way to the dead-letter queue, held in none of the maps above
    private int deadLettering;

    private WorkDispatcher(Vertx vertx,
                           String queue,
                           int shard,
                           String groupName,
                           ShardLog shardLog,
                           ConsumerOffsetRepository groupOffsets,
                           LongSupplier committedOffset,
                           Supplier<Future<Void>> replicateOffsets,
                           Function<ShardEntry, Future<Void>> deadLetter,
                           long lowWatermark) {
        this.vertx = vertx;
        this.queue = queue;
        this.shard = shard;
        this.groupName = groupName;
        this.shardLog = shardLog;
        this.groupOffsets = groupOffsets;
        this.committedOffset = committedOffset;
        this.replicateOffsets = replicateOffsets;
        this.deadLetter = deadLetter;
        this.lowWatermark = lowWatermark;
        this.nextToLease = lowWatermark;
    }

    /**
     * Opens the group's dispatcher at the group's stored position, or at {@code startPosition} when it has none. A
     * resolved latest position is stored and replicated before the dispatcher opens, so a later owner starts the group
     * from the same offset.
     *
     * @param replicateOffsets copies the shard's offsets to a majority of its copies
     * @param deadLetter       appends a record to the group's dead-letter queue
     */
    static Future<WorkDispatcher> open(Vertx vertx,
                                       String queue,
                                       int shard,
                                       String groupName,
                                       ShardLog shardLog,
                                       ConsumerOffsetRepository groupOffsets,
                                       LongSupplier committedOffset,
                                       Supplier<Future<Void>> replicateOffsets,
                                       Function<ShardEntry, Future<Void>> deadLetter,
                                       StartPosition startPosition) {
        long latest = committedOffset.getAsLong();
        return vertx.executeBlocking(() -> {
                        long stored = groupOffsets.findNextOffset(groupName, shard);
                        long ret = stored;
                        if (stored == 0 && startPosition == StartPosition.LATEST) {
                            ret = latest;
                            groupOffsets.save(groupName, shard, latest);
                        }
                        return ret;
                    }, false)
                    .compose(position -> replicateOffsets.get().map(new WorkDispatcher(vertx, queue, shard, groupName, shardLog, groupOffsets,
                                                                                       committedOffset, replicateOffsets, deadLetter,
                                                                                       position)));
    }

    /**
     * Leases up to {@code max} records to the worker, holding the request up to {@link ShardOwner#FETCH_WAIT_MS} while
     * there is none to lease.
     */
    Future<LeaseResponse> lease(String workerId, int max, long leaseMillis) {
        lastRequest = System.currentTimeMillis();
        Future<LeaseResponse> ret;
        if (hasLeasable()) {
            ret = leaseNow(workerId, max, leaseMillis);
        } else {
            PendingLease pending = new PendingLease(workerId, max, leaseMillis, Promise.promise());
            pendingLeases.add(pending);
            vertx.setTimer(ShardOwner.FETCH_WAIT_MS, t -> {
                if (pendingLeases.remove(pending)) {
                    pending.promise().complete(new LeaseResponse(List.of()));
                }
            });
            ret = pending.promise().future();
        }
        return ret;
    }

    /**
     * Applies what the worker did with a record leased to it.
     *
     * @return completes once the group's position on a majority of the shard's copies covers every record done up to
     * it; fails with {@link QueueFailure#LEASE_EXPIRED} when the record is not leased to the worker
     */
    Future<Void> settle(String workerId, Settlement settlement, long offset) {
        lastRequest = System.currentTimeMillis();
        Lease lease = leased.get(offset);
        if (lease == null || !lease.workerId().equals(workerId)) {
            return Future.failedFuture(QueueFailure.LEASE_EXPIRED.exception("Offset " + offset + " of shard " + shard + " of queue "
                                                                                    + queue + " is not leased to this worker"));
        }
        vertx.cancelTimer(lease.timerId());
        Future<Void> ret = Future.succeededFuture();
        switch (settlement) {
            case RENEW -> leased.put(offset, new Lease(workerId, lease.deliveryCount(), lease.leaseMillis(),
                                                       vertx.setTimer(lease.leaseMillis(), t -> expire(offset, workerId))));
            case RELEASE -> {
                leased.remove(offset);
                released.put(offset, lease.deliveryCount());
                servePendingLeases();
            }
            case RETURN -> {
                leased.remove(offset);
                released.put(offset, lease.deliveryCount() - 1);
                // A request still waiting from the closing worker would take the record straight back
                List<PendingLease> closing = pendingLeases.stream().filter(pending -> pending.workerId().equals(workerId)).toList();
                pendingLeases.removeAll(closing);
                closing.forEach(pending -> pending.promise().tryComplete(new LeaseResponse(List.of())));
                servePendingLeases();
            }
            case ACCEPT -> {
                leased.remove(offset);
                ret = markDone(offset);
            }
            case REJECT -> {
                leased.remove(offset);
                ret = deadLetterThenMarkDone(offset);
            }
        }
        return ret;
    }

    /**
     * Leases newly committed records to waiting workers.
     */
    void onCommitted() {
        servePendingLeases();
    }

    /**
     * @return whether the group has no record in flight on the shard, no worker waiting, and no request since
     * {@code since}, in epoch milliseconds; its position is then stored up to every record it finished
     */
    boolean isIdleSince(long since) {
        return lastRequest < since && leased.isEmpty() && released.isEmpty() && done.isEmpty() && pendingLeases.isEmpty()
                && deadLettering == 0;
    }

    void stop() {
        stopped = true;
        leased.values().forEach(lease -> vertx.cancelTimer(lease.timerId()));
        QueueFailureException notOwner = QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " changed owner");
        pendingLeases.forEach(pending -> pending.promise().tryFail(notOwner));
        pendingLeases.clear();
    }

    // Records the retention deleted are gone for the group too, so its position moves up to the shard's start
    private void skipDeleted() {
        long start = shardLog.start().offset();
        if (lowWatermark < start) {
            leased.headMap(start).values().forEach(lease -> vertx.cancelTimer(lease.timerId()));
            leased.headMap(start).clear();
            released.headMap(start).clear();
            done.headSet(start).clear();
            log.warn("Group {} had not finished offsets {} to {} of shard {} of queue {}, which the retention deleted",
                     groupName, lowWatermark, start, shard, queue);
            lowWatermark = start;
            while (done.remove(lowWatermark)) {
                lowWatermark++;
            }
            nextToLease = Math.max(nextToLease, lowWatermark);
            saveLowWatermark();
        }
    }

    private boolean hasLeasable() {
        skipDeleted();
        return !released.isEmpty() || (nextToLease < committedOffset.getAsLong() && nextToLease - lowWatermark < MAX_IN_FLIGHT);
    }

    // Takes the offsets to lease on the context, so concurrent requests never take the same one, then reads them
    private Future<LeaseResponse> leaseNow(String workerId, int max, long leaseMillis) {
        Map<Long, Integer> taken = new TreeMap<>();
        List<Long> exhausted = new ArrayList<>();
        Iterator<Map.Entry<Long, Integer>> releasedIterator = released.entrySet().iterator();
        while (taken.size() < max && releasedIterator.hasNext()) {
            Map.Entry<Long, Integer> entry = releasedIterator.next();
            releasedIterator.remove();
            if (entry.getValue() >= MAX_DELIVERIES) {
                exhausted.add(entry.getKey());
            } else {
                taken.put(entry.getKey(), entry.getValue());
            }
        }
        long newFrom = nextToLease;
        long newTo = Math.min(Math.min(committedOffset.getAsLong(), lowWatermark + MAX_IN_FLIGHT), newFrom + max - taken.size());
        nextToLease = Math.max(nextToLease, newTo);
        // Dead-lettered only once this request has taken its offsets, since marking them done serves other waiting requests
        exhausted.forEach(this::deadLetterThenMarkDone);
        return vertx.executeBlocking(() -> readTaken(taken, newFrom, newTo), false)
                    .transform(ar -> {
                        Future<LeaseResponse> ret;
                        if (ar.failed() || stopped) {
                            // The taken offsets are leased again on the next request
                            taken.forEach(released::put);
                            for (long offset = newFrom; offset < newTo; offset++) {
                                released.put(offset, 0);
                            }
                            ret = Future.failedFuture(ar.failed() ? ar.cause() : QueueFailure.NOT_OWNER.exception("Shard " + shard
                                                                                                                         + " of queue " + queue + " changed owner"));
                        } else {
                            ret = Future.succeededFuture(grant(workerId, leaseMillis, taken, newFrom, newTo, ar.result()));
                        }
                        return ret;
                    });
    }

    private List<ShardEntry> readTaken(Map<Long, Integer> taken, long newFrom, long newTo) {
        List<ShardEntry> ret = new ArrayList<>();
        for (long offset : taken.keySet()) {
            ret.addAll(shardLog.read(offset, 1, Long.MAX_VALUE));
        }
        if (newTo > newFrom) {
            ret.addAll(shardLog.read(newFrom, (int) (newTo - newFrom), ShardOwner.MAX_BATCH_BYTES));
        }
        return ret;
    }

    private LeaseResponse grant(String workerId,
                                long leaseMillis,
                                Map<Long, Integer> taken,
                                long newFrom,
                                long newTo,
                                List<ShardEntry> read) {
        List<LeasedEntry> ret = new ArrayList<>();
        long newRead = newFrom;
        for (ShardEntry entry : read) {
            if (entry.offset() >= newFrom) {
                newRead = entry.offset() + 1;
            }
            if (!entry.isRecord()) {
                markDone(entry.offset());
            } else {
                int deliveryCount = taken.getOrDefault(entry.offset(), 0) + 1;
                long offset = entry.offset();
                long timerId = vertx.setTimer(leaseMillis, t -> expire(offset, workerId));
                leased.put(offset, new Lease(workerId, deliveryCount, leaseMillis, timerId));
                ret.add(new LeasedEntry(entry, deliveryCount));
            }
        }
        // A read cut short by its byte budget leaves the rest of the taken range for the next request
        for (long offset = newRead; offset < newTo; offset++) {
            released.put(offset, 0);
        }
        return new LeaseResponse(ret);
    }

    private void expire(long offset, String workerId) {
        Lease lease = leased.get(offset);
        if (lease != null && lease.workerId().equals(workerId)) {
            leased.remove(offset);
            released.put(offset, lease.deliveryCount());
            servePendingLeases();
        }
    }

    private void servePendingLeases() {
        while (!stopped && !pendingLeases.isEmpty() && hasLeasable()) {
            PendingLease pending = pendingLeases.removeFirst();
            leaseNow(pending.workerId(), pending.max(), pending.leaseMillis()).onComplete(pending.promise());
        }
    }

    // Marks the record done once it is in the dead-letter queue. A record that could not be appended there waits among
    // the released records, at its last delivery, so the next lease request tries again.
    private Future<Void> deadLetterThenMarkDone(long offset) {
        deadLettering++;
        return vertx.executeBlocking(() -> shardLog.read(offset, 1, Long.MAX_VALUE).getFirst(), false)
                    .compose(deadLetter)
                    .transform(ar -> {
                        deadLettering--;
                        Future<Void> ret;
                        if (stopped) {
                            ret = Future.failedFuture(QueueFailure.NOT_OWNER.exception("Shard " + shard + " of queue " + queue + " changed owner"));
                        } else if (offset < lowWatermark) {
                            // The retention deleted the record meanwhile, and the group moved past it
                            ret = Future.succeededFuture();
                        } else if (ar.failed()) {
                            log.warn("Appending offset {} of shard {} of queue {} to the dead-letter queue of group {} failed, retrying",
                                     offset, shard, queue, groupName, ar.cause());
                            released.put(offset, MAX_DELIVERIES);
                            ret = Future.failedFuture(ar.cause());
                        } else {
                            log.info("Dead-lettered offset {} of shard {} of queue {} for group {}", offset, shard, queue, groupName);
                            ret = markDone(offset);
                        }
                        return ret;
                    });
    }

    // Returns the replication of the advanced watermark, or a completed future when the record is past a record not yet done
    private Future<Void> markDone(long offset) {
        // A record before the low watermark was deleted by the retention and skipped
        if (offset >= lowWatermark) {
            done.add(offset);
        }
        long before = lowWatermark;
        while (done.remove(lowWatermark)) {
            lowWatermark++;
        }
        Future<Void> ret = Future.succeededFuture();
        if (lowWatermark > before) {
            ret = saveLowWatermark();
            servePendingLeases();
        }
        return ret;
    }

    private Future<Void> saveLowWatermark() {
        long watermark = lowWatermark;
        lastSave = lastSave.transform(ignored -> vertx.<Void>executeBlocking(() -> {
            groupOffsets.save(groupName, shard, watermark);
            return null;
        }, false));
        lastSave.onFailure(e -> log.warn("Saving the position of group {} on shard {} of queue {} failed", groupName, shard, queue, e));
        return lastSave.compose(v -> replicateOffsets.get());
    }
}
