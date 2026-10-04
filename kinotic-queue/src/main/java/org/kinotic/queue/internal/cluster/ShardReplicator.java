package org.kinotic.queue.internal.cluster;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.internal.cluster.message.ReplicateRequest;
import org.kinotic.queue.internal.log.ConsumerOffsetRepository;
import org.kinotic.queue.internal.log.LogStart;
import org.kinotic.queue.internal.log.ReplicationBatch;
import org.kinotic.queue.internal.log.ReplicationResult;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardLog;

import java.util.List;
import java.util.Map;

/**
 * Copies an owned shard to one follower, one batch at a time, reading the batches from the owner's copy. A follower
 * that is behind, or holds entries the owner does not, is sent everything from the last entry the two agree on. The
 * shard's consumer and group offsets travel with a batch whenever they changed since the follower last received them.
 * A follower sent nothing for {@link #HEARTBEAT_INTERVAL_MS} is sent an empty batch, so the owner learns it still
 * answers. Runs on the owner's context.
 */
@Slf4j
final class ShardReplicator {

    /**
     * How long a follower goes without a batch before it is sent an empty one.
     */
    static final long HEARTBEAT_INTERVAL_MS = 1_000;

    private static final int BATCH_SIZE = 256;
    private static final long RETRY_DELAY_MS = 500;

    private final Vertx vertx;
    private final QueueClusterClient client;
    private final String follower;
    private final String queue;
    private final int shard;
    private final ShardLog shardLog;
    private final ConsumerOffsetRepository consumerOffsets;
    private final ConsumerOffsetRepository groupOffsets;
    private final long epoch;
    private final Runnable onProgress;
    private final Runnable onStaleEpoch;

    private long nextToSend;
    // The epoch of the follower's entry that differed from this copy's, reported with the last mismatch; -1 when none
    private long conflictEpoch = -1;
    private long matchedOffset;
    private long replicatedOffsetsVersion = -1;
    private boolean inFlight;
    private boolean changedWhileInFlight;
    private boolean stopped;
    private long retryTimer = -1;
    private long lastSentMillis;
    private long lastAnsweredMillis = System.currentTimeMillis();

    /**
     * @param onProgress   called when the follower holds more of the shard, or newer offsets, than before
     * @param onStaleEpoch called when the follower has promised a newer owner
     */
    ShardReplicator(Vertx vertx,
                    QueueClusterClient client,
                    String follower,
                    String queue,
                    int shard,
                    ShardLog shardLog,
                    ConsumerOffsetRepository consumerOffsets,
                    ConsumerOffsetRepository groupOffsets,
                    long epoch,
                    Runnable onProgress,
                    Runnable onStaleEpoch) {
        this.vertx = vertx;
        this.client = client;
        this.follower = follower;
        this.queue = queue;
        this.shard = shard;
        this.shardLog = shardLog;
        this.consumerOffsets = consumerOffsets;
        this.groupOffsets = groupOffsets;
        this.epoch = epoch;
        this.onProgress = onProgress;
        this.onStaleEpoch = onStaleEpoch;
        this.nextToSend = shardLog.nextOffset();
    }

    void start() {
        send();
    }

    /**
     * The version of the owner's consumer and group offsets for the shard, which grows whenever either changes.
     */
    static long offsetsVersion(ConsumerOffsetRepository consumerOffsets, ConsumerOffsetRepository groupOffsets, int shard) {
        // Both versions only grow, so their sum grows whenever either repository changes
        return consumerOffsets.version(shard) + groupOffsets.version(shard);
    }

    /**
     * Sends the owner's new entries and offsets, once the batch on its way is answered or right away when none is,
     * unless a batch is waiting to be retried.
     */
    void notifyChanged() {
        if (inFlight) {
            changedWhileInFlight = true;
        } else if (retryTimer < 0) {
            send();
        }
    }

    /**
     * Sends an empty batch when nothing was sent for {@link #HEARTBEAT_INTERVAL_MS}.
     */
    void heartbeat() {
        if (!inFlight && retryTimer < 0 && System.currentTimeMillis() - lastSentMillis >= HEARTBEAT_INTERVAL_MS) {
            send();
        }
    }

    /**
     * @return when the last batch the follower answered was sent, in epoch milliseconds; when the replicator was
     * created until the follower first answers
     */
    long lastAnsweredMillis() {
        return lastAnsweredMillis;
    }

    /**
     * @return the offset up to which the follower holds the same entries as the owner
     */
    long matchedOffset() {
        return matchedOffset;
    }

    /**
     * @return the {@link #offsetsVersion} of the owner's offsets the follower holds
     */
    long replicatedOffsetsVersion() {
        return replicatedOffsetsVersion;
    }

    void stop() {
        stopped = true;
        if (retryTimer >= 0) {
            vertx.cancelTimer(retryTimer);
        }
    }

    private void send() {
        if (!stopped && !inFlight) {
            inFlight = true;
            long sentMillis = System.currentTimeMillis();
            lastSentMillis = sentMillis;
            // A follower can report an offset past the owner's end when it holds entries the owner does not
            long hint = Math.min(nextToSend, shardLog.nextOffset());
            long conflict = conflictEpoch;
            conflictEpoch = -1;
            long knownVersion = replicatedOffsetsVersion;
            long[] offsetsVersion = new long[1];
            vertx.executeBlocking(() -> {
                     offsetsVersion[0] = offsetsVersion(consumerOffsets, groupOffsets, shard);
                     boolean changed = offsetsVersion[0] > knownVersion;
                     return batch(resumeAfterConflict(hint, conflict),
                                  changed ? consumerOffsets.findAll(shard) : Map.of(),
                                  changed ? groupOffsets.findAll(shard) : Map.of());
                 }, false)
                 .compose(request -> client.replicate(follower, request))
                 .onComplete(reply -> onReply(reply, offsetsVersion[0], sentMillis));
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

    private ReplicateRequest batch(long from, Map<String, Long> consumers, Map<String, Long> groups) {
        // A follower that ends before this copy's start receives the entries from the start, and drops its own
        LogStart start = shardLog.start();
        long first = Math.max(from, start.offset());
        // The epoch of the entry before the batch lets the follower check it continues the same history
        long prevEpoch = shardLog.epochAt(first - 1);
        List<ShardEntry> entries = shardLog.read(first, BATCH_SIZE, ShardOwner.MAX_BATCH_BYTES);
        // Read after the entries, so the owner's end covers every entry in the batch
        long ownerNextOffset = shardLog.nextOffset();
        return new ReplicateRequest(queue, shard, new ReplicationBatch(epoch, first - 1, prevEpoch, start.offset(), ownerNextOffset, entries),
                                    consumers, groups);
    }

    private void onReply(AsyncResult<ReplicationResult> reply, long offsetsVersion, long sentMillis) {
        inFlight = false;
        boolean changed = changedWhileInFlight;
        changedWhileInFlight = false;
        if (stopped) {
            return;
        }
        if (reply.failed()) {
            log.debug("Replicating shard {} of queue {} to {} failed, retrying", shard, queue, follower, reply.cause());
            retryTimer = vertx.setTimer(RETRY_DELAY_MS, t -> {
                retryTimer = -1;
                send();
            });
        } else {
            ReplicationResult result = reply.result();
            lastAnsweredMillis = Math.max(lastAnsweredMillis, sentMillis);
            switch (result.status()) {
                case ACCEPTED -> {
                    boolean progressed = offsetsVersion > replicatedOffsetsVersion || result.nextOffset() > matchedOffset;
                    replicatedOffsetsVersion = offsetsVersion;
                    nextToSend = result.nextOffset();
                    matchedOffset = Math.max(matchedOffset, result.nextOffset());
                    if (progressed) {
                        onProgress.run();
                    }
                    if (changed || nextToSend < shardLog.nextOffset()) {
                        send();
                    }
                }
                case MISMATCH -> {
                    boolean progressed = offsetsVersion > replicatedOffsetsVersion;
                    replicatedOffsetsVersion = offsetsVersion;
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
