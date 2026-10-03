package org.kinotic.queue.internal.cluster;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.internal.cluster.message.ReplicateRequest;
import org.kinotic.queue.internal.log.ReplicationResult;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardLog;

import java.util.List;

/**
 * Copies an owned shard to one follower, one batch at a time, reading the batches from the owner's copy. A follower
 * that is behind, or was emptied because it held entries the owner does not, is sent everything it is missing.
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
    private final long epoch;
    private final Runnable onMatched;
    private final Runnable onStaleEpoch;

    private long nextToSend;
    private long matchedOffset;
    private boolean inFlight;
    private boolean stopped;
    private long retryTimer = -1;

    /**
     * @param onMatched    called when the follower holds more of the shard than before
     * @param onStaleEpoch called when the follower has accepted a newer owner
     */
    ShardReplicator(Vertx vertx,
                    QueueClusterClient client,
                    String follower,
                    String queue,
                    int shard,
                    ShardLog shardLog,
                    long epoch,
                    Runnable onMatched,
                    Runnable onStaleEpoch) {
        this.vertx = vertx;
        this.client = client;
        this.follower = follower;
        this.queue = queue;
        this.shard = shard;
        this.shardLog = shardLog;
        this.epoch = epoch;
        this.onMatched = onMatched;
        this.onStaleEpoch = onStaleEpoch;
        this.nextToSend = shardLog.nextOffset();
    }

    void start() {
        send();
    }

    /**
     * Sends the owner's new entries unless a batch is already on its way.
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
            long from = nextToSend;
            vertx.executeBlocking(() -> batch(from), false)
                 .compose(request -> client.replicate(follower, request))
                 .onComplete(this::onReply);
        }
    }

    private ReplicateRequest batch(long from) {
        // Reads the entry before the batch too, whose epoch lets the follower check it continues the same history
        List<ShardEntry> read = shardLog.read(Math.max(0, from - 1), BATCH_SIZE + 1);
        long prevEpoch = -1;
        List<ShardEntry> entries = read;
        if (from > 0) {
            prevEpoch = read.getFirst().epoch();
            entries = read.subList(1, read.size());
        }
        // Read after the entries, so the owner's end covers every entry in the batch
        long ownerNextOffset = shardLog.nextOffset();
        return new ReplicateRequest(queue, shard, epoch, from - 1, prevEpoch, ownerNextOffset, List.copyOf(entries));
    }

    private void onReply(AsyncResult<ReplicationResult> reply) {
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
                    nextToSend = result.nextOffset();
                    // An emptied follower no longer holds what it held before
                    matchedOffset = Math.min(matchedOffset, result.nextOffset());
                    send();
                }
                case STALE_EPOCH -> onStaleEpoch.run();
            }
        }
    }
}
