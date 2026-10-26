package org.kinotic.queue.internal.cluster;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.internal.cluster.message.ReplicateRequest;
import org.kinotic.queue.internal.cluster.message.ShardId;
import org.kinotic.queue.internal.log.LogStart;
import org.kinotic.queue.internal.log.QueueLog;
import org.kinotic.queue.internal.log.ReplicationBatch;
import org.kinotic.queue.internal.log.ReplicationResult;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardLog;
import org.kinotic.queue.internal.log.ShardPositions;

import java.util.List;

/**
 * Copies an owned shard to one follower, one batch at a time, reading the batches from the owner's copy. A follower
 * that is behind, or holds entries the owner does not, is sent everything from the last entry the two agree on. The
 * shard's consumer and group positions travel with a batch whenever they changed since the follower last received them.
 * A follower sent nothing for {@link #HEARTBEAT_INTERVAL_MS} is sent an empty batch, so the owner learns it still
 * answers. Runs on the owner's context.
 */
@Slf4j
@Accessors(fluent = true)
@RequiredArgsConstructor
final class ShardReplicator {

    /**
     * How long a follower goes without a batch before it is sent an empty one.
     */
    private static final long HEARTBEAT_INTERVAL_MS = 1_000;

    private static final int BATCH_SIZE = 256;
    private static final long RETRY_DELAY_MS = 500;

    private final Vertx vertx;
    private final QueueClusterClient client;
    private final String follower;
    private final ShardId shardId;
    // The owner's copy of the shard's queue, whose positions are replicated
    private final QueueLog queueLog;
    // The owner's copy of the shard
    private final ShardLog shardLog;
    private final long epoch;
    // Called when the follower holds more of the shard, or newer positions, than before
    private final Runnable onProgress;
    // Called when the follower has promised a newer owner
    private final Runnable onStaleEpoch;

    private long nextToSend;
    // The epoch of the follower's entry that differed from this copy's, reported with the last mismatch; -1 when none
    private long conflictEpoch = -1;
    /**
     * The offset up to which the follower holds the same entries as the owner.
     */
    @Getter
    private long matchedOffset;
    /**
     * The {@link QueueLog#positionsVersion version} of the owner's positions the follower holds, or -1 before it holds
     * any; a batch carries only the positions changed since.
     */
    @Getter
    private long replicatedPositionsVersion = -1;
    // Set while a batch is on its way or waits to be sent again; changes made meanwhile are sent once it is answered
    private boolean inFlight;
    private boolean stopped;
    private long lastSentMillis;
    // The epoch of this copy's entry at knownEpochOffset, from the last batch built, whose last entry or previous entry
    // is usually the next batch's previous entry
    private long knownEpochOffset = -2;
    private long knownEpoch;
    /**
     * When the last batch the follower answered was sent, as {@link MonotonicClock#millis()} read it; when the
     * replicator was created until the follower first answers.
     */
    @Getter
    private long lastAnsweredMillis = MonotonicClock.millis();

    void start() {
        nextToSend = shardLog.nextOffset();
        send();
    }

    /**
     * Sends the owner's new entries and positions, right away when no batch is on its way or waiting to be sent again,
     * otherwise once that batch is answered.
     */
    void notifyChanged() {
        send();
    }

    /**
     * Sends an empty batch when nothing was sent for {@link #HEARTBEAT_INTERVAL_MS}.
     */
    void heartbeat() {
        if (MonotonicClock.millis() - lastSentMillis >= HEARTBEAT_INTERVAL_MS) {
            send();
        }
    }

    void stop() {
        stopped = true;
    }

    private void send() {
        if (!stopped && !inFlight) {
            inFlight = true;
            long sentMillis = MonotonicClock.millis();
            lastSentMillis = sentMillis;
            // A follower can report an offset past the owner's end when it holds entries the owner does not
            long hint = Math.min(nextToSend, shardLog.nextOffset());
            long conflict = conflictEpoch;
            conflictEpoch = -1;
            // Read before the positions, so the positions sent include every change up to it
            long version = queueLog.positionsVersion(shardId.shard());
            long held = replicatedPositionsVersion;
            long epochOffset = knownEpochOffset;
            long offsetEpoch = knownEpoch;
            vertx.executeBlocking(() -> batch(resumeAfterConflict(hint, conflict),
                                              version == held ? ShardPositions.NONE : queueLog.positionsChangedSince(shardId.shard(), held),
                                              epochOffset, offsetEpoch), false)
                 .onSuccess(this::rememberLastEpoch)
                 .compose(request -> client.replicate(follower, request))
                 .onComplete(reply -> onReply(reply, version, sentMillis));
        }
    }

    // Where a follower whose entry of the conflicting epoch differed from this copy's continues: after this copy's last
    // entry of that epoch when it holds any, since the follower holds the same entries of it up to there, otherwise at
    // the follower's first entry of it. Each mismatch then steps back by a whole epoch, not one entry.
    private long resumeAfterConflict(long followerFirstOfEpoch, long conflict) {
        long ret = followerFirstOfEpoch;
        if (conflict >= 0) {
            long end = shardLog.endOfEpoch(conflict);
            if (shardLog.epochAt(end - 1) == conflict) {
                ret = end;
            }
        }
        return ret;
    }

    // The epoch at knownOffset saves reading the entry before the batch again
    private ReplicateRequest batch(long from, ShardPositions positions, long knownOffset, long knownOffsetEpoch) {
        // A follower that ends before this copy's start receives the entries from the start, and drops its own
        LogStart start = shardLog.start();
        long first = Math.max(from, start.offset());
        // The epoch of the entry before the batch lets the follower check it continues the same history
        long prevEpoch = first - 1 == knownOffset ? knownOffsetEpoch : shardLog.epochAt(first - 1);
        List<ShardEntry> entries = shardLog.read(first, BATCH_SIZE, ShardOwner.MAX_BATCH_BYTES);
        // Read after the entries, so the owner's end covers every entry in the batch
        long ownerNextOffset = shardLog.nextOffset();
        return new ReplicateRequest(shardId, new ReplicationBatch(epoch, first - 1, prevEpoch, start.offset(), ownerNextOffset, entries),
                                    positions);
    }

    // This copy's entries keep their epochs while it is the owner's, so an epoch read once stays true
    private void rememberLastEpoch(ReplicateRequest request) {
        List<ShardEntry> entries = request.batch().entries();
        if (entries.isEmpty()) {
            knownEpochOffset = request.batch().prevOffset();
            knownEpoch = request.batch().prevEpoch();
        } else {
            knownEpochOffset = entries.getLast().offset();
            knownEpoch = entries.getLast().epoch();
        }
    }

    private void onReply(AsyncResult<ReplicationResult> reply, long positionsVersion, long sentMillis) {
        if (stopped) {
            return;
        }
        if (reply.failed()) {
            log.debug("Replicating {} to {} failed, retrying", shardId, follower, reply.cause());
            vertx.setTimer(RETRY_DELAY_MS, t -> {
                inFlight = false;
                send();
            });
        } else {
            inFlight = false;
            ReplicationResult result = reply.result();
            lastAnsweredMillis = Math.max(lastAnsweredMillis, sentMillis);
            switch (result.status()) {
                case ACCEPTED -> {
                    boolean progressed = positionsVersion > replicatedPositionsVersion || result.nextOffset() > matchedOffset;
                    replicatedPositionsVersion = positionsVersion;
                    nextToSend = result.nextOffset();
                    matchedOffset = Math.max(matchedOffset, result.nextOffset());
                    if (progressed) {
                        onProgress.run();
                    }
                    // Entries appended or positions changed while the batch was on its way
                    if (nextToSend < shardLog.nextOffset() || queueLog.positionsVersion(shardId.shard()) > replicatedPositionsVersion) {
                        send();
                    }
                }
                case MISMATCH -> {
                    boolean progressed = positionsVersion > replicatedPositionsVersion;
                    replicatedPositionsVersion = positionsVersion;
                    nextToSend = result.nextOffset();
                    conflictEpoch = result.conflictEpoch();
                    matchedOffset = Math.min(matchedOffset, result.nextOffset());
                    if (progressed) {
                        onProgress.run();
                    }
                    send();
                }
                case STALE_EPOCH -> onStaleEpoch.run();
            }
        }
    }
}
