package org.kinotic.queue.internal.cluster;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.internal.cluster.message.LeaseResponse;
import org.kinotic.queue.internal.cluster.message.LeasedEntry;
import org.kinotic.queue.internal.cluster.message.Settlement;
import org.kinotic.queue.internal.cluster.message.ShardId;
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
 * are skipped. The group's low watermark, the offset before which every record is done, is stored in the queue's group
 * offsets and copied to a majority of the shard's copies, so the shard's next owner resumes the group from it. A lease
 * can be renewed while its worker still processes the record. Runs on the owner's context.
 */
@Slf4j
@RequiredArgsConstructor
final class WorkDispatcher {

    /**
     * How many times a record is leased before it goes to the group's dead-letter queue.
     */
    private static final int MAX_DELIVERIES = 5;

    // Bounds the records past the low watermark, which the owner tracks in memory and redelivers after a change of owner
    private static final int MAX_IN_FLIGHT = 1_000;
    // A dispatcher with nothing in flight is dropped after this long without a request, or half the retention period
    // when that is shorter, so a group gone that long starts over once its stored position expires
    private static final long IDLE_MS = 5 * 60_000;

    private final Vertx vertx;
    private final ShardId shardId;
    private final String groupName;
    private final ShardLog shardLog;
    private final ConsumerOffsetRepository groupOffsets;
    private final LongSupplier committedOffset;
    // Copies the shard's offsets to a majority of its copies
    private final Supplier<Future<Void>> replicateOffsets;
    // Appends a record to the group's dead-letter queue
    private final Function<ShardEntry, Future<Void>> deadLetter;
    private final TreeMap<Long, Lease> leased = new TreeMap<>();
    // Records waiting to be leased again, with how many times they have been leased
    private final TreeMap<Long, Integer> released = new TreeMap<>();
    // Done records past the low watermark
    private final TreeSet<Long> done = new TreeSet<>();
    private final List<PendingLease> pendingLeases = new ArrayList<>();

    private long lowWatermark;
    private long nextToLease;
    // One save of the low watermark runs at a time, of the watermark when it began, so a later watermark is never
    // overwritten by an earlier one. The watermark only grows, so the running save answers every request made before
    // it began, and the requests made while it runs share the next save. Each is null while there is none.
    private Promise<Void> saving;
    private Promise<Void> nextSave;
    private boolean stopped;
    private long lastRequest = MonotonicClock.millis();
    // When the low watermark was last stored, which keeps the group's position from expiring
    private long lastSaved;
    // Records on their way to the dead-letter queue, held in none of the maps above
    private int deadLettering;

    /**
     * Opens the group's dispatcher at the group's stored position, or at {@code startPosition} when it has none. A
     * resolved latest position is stored and replicated before the dispatcher opens, so a later owner starts the group
     * from the same offset.
     *
     * @return this dispatcher, once it is open
     */
    Future<WorkDispatcher> open(StartPosition startPosition) {
        long latest = committedOffset.getAsLong();
        return vertx.executeBlocking(() -> {
                        long stored = groupOffsets.findNextOffset(groupName, shardId.shard());
                        long ret = stored;
                        if (stored == 0 && startPosition == StartPosition.LATEST) {
                            ret = latest;
                            groupOffsets.save(groupName, shardId.shard(), latest);
                        }
                        return ret;
                    }, false)
                    .compose(position -> {
                        lowWatermark = position;
                        nextToLease = position;
                        return replicateOffsets.get().map(this);
                    });
    }

    /**
     * Leases up to {@code max} records to the worker, holding the request up to {@link ShardOwner#FETCH_WAIT_MS} while
     * there is none to lease.
     */
    Future<LeaseResponse> lease(String workerId, int max, long leaseMillis) {
        lastRequest = MonotonicClock.millis();
        // A group whose workers wait on a shard with no new records makes no progress, so its position is used again
        if (lowWatermark > 0 && lastRequest - lastSaved >= groupOffsets.getRefreshInterval().toMillis()) {
            saveLowWatermark();
        }
        Future<LeaseResponse> ret;
        if (hasLeasable()) {
            ret = leaseNow(workerId, max, leaseMillis);
        } else {
            Promise<LeaseResponse> promise = Promise.promise();
            long timerId = vertx.setTimer(ShardOwner.FETCH_WAIT_MS, t -> {
                if (pendingLeases.removeIf(pending -> pending.promise() == promise)) {
                    promise.complete(new LeaseResponse(List.of()));
                }
            });
            pendingLeases.add(new PendingLease(workerId, max, leaseMillis, promise, timerId));
            ret = promise.future();
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
        lastRequest = MonotonicClock.millis();
        Lease lease = leased.get(offset);
        if (lease == null || !lease.workerId().equals(workerId)) {
            return Future.failedFuture(QueueFailure.LEASE_EXPIRED.exception("Offset " + offset + " of " + shardId
                                                                                    + " is not leased to this worker"));
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
                closing.forEach(pending -> {
                    vertx.cancelTimer(pending.timerId());
                    pending.promise().tryComplete(new LeaseResponse(List.of()));
                });
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
     * @param now the current {@link MonotonicClock#millis()} reading
     * @return whether no worker of the group holds or waits for a record of the shard, no record is on its way to the
     * dead-letter queue, and no request came for five minutes or half the retention period, whichever is shorter, or
     * for the whole retention period while records wait to be leased again. The group's position is then stored up to
     * the first record it has not finished; the records it finished past that are leased again once the group's
     * dispatcher is opened again.
     */
    boolean isIdle(long now) {
        // Released records keep their delivery counts until the group's position expires, so a record that fails every
        // worker still reaches the dead-letter queue
        long idleFor = released.isEmpty() ? Math.min(IDLE_MS, 2 * groupOffsets.getRefreshInterval().toMillis())
                                          : groupOffsets.getExpiry().toMillis();
        return lastRequest < now - idleFor && leased.isEmpty() && pendingLeases.isEmpty() && deadLettering == 0;
    }

    void stop() {
        stopped = true;
        leased.values().forEach(lease -> vertx.cancelTimer(lease.timerId()));
        QueueFailureException notOwner = ShardOwner.changedOwner(shardId);
        pendingLeases.forEach(pending -> {
            vertx.cancelTimer(pending.timerId());
            pending.promise().tryFail(notOwner);
        });
        pendingLeases.clear();
        if (saving != null) {
            saving.tryFail(notOwner);
        }
        if (nextSave != null) {
            nextSave.tryFail(notOwner);
        }
    }

    // Records the retention deleted are gone for the group too, so its position moves up to the shard's start
    private void skipDeleted() {
        long start = shardLog.start().offset();
        if (lowWatermark < start) {
            leased.headMap(start).values().forEach(lease -> vertx.cancelTimer(lease.timerId()));
            leased.headMap(start).clear();
            released.headMap(start).clear();
            done.headSet(start).clear();
            log.warn("Group {} had not finished offsets {} to {} of {}, which the retention deleted", groupName, lowWatermark, start, shardId);
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
                            ret = Future.failedFuture(ar.failed() ? ar.cause() : ShardOwner.changedOwner(shardId));
                        } else {
                            ret = Future.succeededFuture(grant(workerId, leaseMillis, taken, newFrom, newTo, ar.result()));
                        }
                        return ret;
                    });
    }

    // Reads each run of consecutive taken offsets at once, then the new ones
    private List<ShardEntry> readTaken(Map<Long, Integer> taken, long newFrom, long newTo) {
        List<ShardEntry> ret = new ArrayList<>();
        long runStart = -1;
        int runLength = 0;
        for (long offset : taken.keySet()) {
            if (offset != runStart + runLength) {
                if (runLength > 0) {
                    ret.addAll(shardLog.read(runStart, runLength, Long.MAX_VALUE));
                }
                runStart = offset;
                runLength = 0;
            }
            runLength++;
        }
        if (runLength > 0) {
            ret.addAll(shardLog.read(runStart, runLength, Long.MAX_VALUE));
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
            vertx.cancelTimer(pending.timerId());
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
                            ret = Future.failedFuture(ShardOwner.changedOwner(shardId));
                        } else if (offset < lowWatermark) {
                            // The retention deleted the record meanwhile, and the group moved past it
                            ret = Future.succeededFuture();
                        } else if (ar.failed()) {
                            log.warn("Appending offset {} of {} to the dead-letter queue of group {} failed, retrying",
                                     offset, shardId, groupName, ar.cause());
                            released.put(offset, MAX_DELIVERIES);
                            ret = Future.failedFuture(ar.cause());
                        } else {
                            log.info("Dead-lettered offset {} of {} for group {}", offset, shardId, groupName);
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

    // Completes once the group's position, at least the current low watermark, is stored and on a majority of copies
    private Future<Void> saveLowWatermark() {
        if (stopped) {
            return Future.failedFuture(ShardOwner.changedOwner(shardId));
        }
        if (nextSave == null) {
            nextSave = Promise.promise();
        }
        Future<Void> ret = nextSave.future();
        saveNext();
        return ret;
    }

    private void saveNext() {
        if (saving == null && nextSave != null && !stopped) {
            Promise<Void> save = nextSave;
            saving = save;
            nextSave = null;
            long watermark = lowWatermark;
            lastSaved = MonotonicClock.millis();
            vertx.<Void>executeBlocking(() -> {
                     groupOffsets.save(groupName, shardId.shard(), watermark);
                     return null;
                 }, false)
                 .compose(v -> replicateOffsets.get())
                 .onComplete(ar -> {
                     saving = null;
                     if (ar.succeeded()) {
                         save.tryComplete();
                     } else {
                         log.warn("Saving the position of group {} on {} failed", groupName, shardId, ar.cause());
                         save.tryFail(ar.cause());
                     }
                     saveNext();
                 });
        }
    }
}
