package org.kinotic.queue.internal.cluster;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.internal.cluster.message.ReplicateRequest;
import org.kinotic.queue.internal.log.ConsumerOffsetRepository;
import org.kinotic.queue.internal.log.ReplicationResult;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardLog;

import java.util.List;
import java.util.Map;

/**
 * Copies an owned shard to one follower, one batch at a time, reading the batches from the owner's copy. A follower
 * that is behind, or holds entries the owner does not, is sent everything from the last entry the two agree on. The
 * shard's consumer and group offsets travel with a batch whenever they changed since the follower last received them.
 * Runs on the owner's context.
 */
@Slf4j
final class ShardReplicator {

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
    private final Runnable onMatched;
    private final Runnable onStaleEpoch;

    private long nextToSend;
    private long matchedOffset;
    private long sentOffsetsVersion = -1;
    private boolean inFlight;
    private boolean stopped;
    private long retryTimer = -1;

    /**
     * @param onMatched    called when the follower holds more of the shard than before
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
                    Runnable onMatched,
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
        this.onMatched = onMatched;
        this.onStaleEpoch = onStaleEpoch;
        this.nextToSend = shardLog.nextOffset();
    }

    void start() {
        send();
    }

    /**
     * Sends the owner's new entries unless a batch is already on its way or waiting to be retried.
     */
    void notifyAppended() {
        if (retryTimer < 0) {
            send();
        }
    }

    /**
     * @return the offset up to which the follower holds the same entries as the owner
     */
    long matchedOffset() {
        return matchedOffset;
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
            // A follower can report an offset past the owner's end when it holds entries the owner does not
            long from = Math.min(nextToSend, shardLog.nextOffset());
            long[] offsetsVersion = new long[1];
            vertx.executeBlocking(() -> {
                     // Both versions only grow, so their sum grows whenever either repository changes
                     offsetsVersion[0] = consumerOffsets.version(shard) + groupOffsets.version(shard);
                     boolean changed = offsetsVersion[0] > sentOffsetsVersion;
                     return batch(from,
                                  changed ? consumerOffsets.findAll(shard) : Map.of(),
                                  changed ? groupOffsets.findAll(shard) : Map.of());
                 }, false)
                 .compose(request -> client.replicate(follower, request))
                 .onComplete(reply -> onReply(reply, offsetsVersion[0]));
        }
    }

    private ReplicateRequest batch(long from, Map<String, Long> consumers, Map<String, Long> groups) {
        // Reads the entry before the batch too, whose epoch lets the follower check it continues the same history
        List<ShardEntry> read = shardLog.read(Math.max(0, from - 1), BATCH_SIZE + 1, ShardOwner.MAX_BATCH_BYTES);
        long prevEpoch = -1;
        List<ShardEntry> entries = read;
        if (from > 0) {
            prevEpoch = read.getFirst().epoch();
            entries = read.subList(1, read.size());
        }
        // Read after the entries, so the owner's end covers every entry in the batch
        long ownerNextOffset = shardLog.nextOffset();
        return new ReplicateRequest(queue, shard, epoch, from - 1, prevEpoch, ownerNextOffset, List.copyOf(entries), consumers, groups);
    }

    private void onReply(AsyncResult<ReplicationResult> reply, long offsetsVersion) {
        inFlight = false;
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
            switch (result.status()) {
                case ACCEPTED -> {
                    sentOffsetsVersion = offsetsVersion;
                    nextToSend = result.nextOffset();
                    if (result.nextOffset() > matchedOffset) {
                        matchedOffset = result.nextOffset();
                        onMatched.run();
                    }
                    if (nextToSend < shardLog.nextOffset()) {
                        send();
                    }
                }
                case MISMATCH -> {
                    sentOffsetsVersion = offsetsVersion;
                    nextToSend = result.nextOffset();
                    matchedOffset = Math.min(matchedOffset, result.nextOffset());
                    send();
                }
                case STALE_EPOCH -> onStaleEpoch.run();
            }
        }
    }
}
