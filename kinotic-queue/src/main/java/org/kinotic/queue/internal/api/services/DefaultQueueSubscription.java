package org.kinotic.queue.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.services.QueueSubscription;
import org.kinotic.queue.internal.cluster.QueueClusterClient;
import org.kinotic.queue.internal.cluster.StoredQueue;
import org.kinotic.queue.internal.cluster.message.FetchResponse;
import org.kinotic.queue.internal.log.ShardEntry;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Delivers a queue's committed records to one consumer. Each shard is fetched from its owner, wherever the owner
 * runs, and the records are handed to the handler on the subscription's context as demand allows.
 */
@Slf4j
public class DefaultQueueSubscription extends ShardPullStream<QueueRecord, FetchResponse> implements QueueSubscription {

    private static final int FETCH_SIZE = 256;
    // Fetching pauses while this many records, or this many bytes of them, wait for demand or are being fetched
    private static final int MAX_PENDING = 1_024;
    private static final long MAX_PENDING_BYTES = 16 * 1024 * 1024;
    // The smallest byte budget one fetch is given, so a nearly full buffer still fetches in useful steps
    private static final long MIN_FETCH_BYTES = 64 * 1024;

    private final QueueClusterClient client;
    private final StoredQueue queue;
    private final String consumerName;
    // The next offset to fetch per shard
    private final long[] fetchOffsets;
    private final long[] committedNextOffsets;
    // The offset after the last record handed to the handler, per shard; a commit may not go past it
    private final long[] deliveredNextOffsets;
    private long pendingBytes;
    private long refreshTimer = -1;
    // The byte budgets of the fetches on their way
    private long reservedBytes;

    private DefaultQueueSubscription(Context context,
                                     QueueClusterClient client,
                                     StoredQueue queue,
                                     String consumerName,
                                     long[] fetchOffsets,
                                     long[] committedNextOffsets) {
        super(context, fetchOffsets.length, "consumer " + consumerName + " of queue " + queue.name());
        this.client = client;
        this.queue = queue;
        this.consumerName = consumerName;
        this.fetchOffsets = fetchOffsets;
        this.committedNextOffsets = committedNextOffsets;
        this.deliveredNextOffsets = fetchOffsets.clone();
    }

    /**
     * Opens a subscription positioned after the consumer's committed offsets. A latest start is resolved before the
     * subscription opens, so it delivers every record appended after it opened.
     */
    static Future<QueueSubscription> open(Context context,
                                          QueueClusterClient client,
                                          StoredQueue queue,
                                          String consumerName,
                                          StartPosition startPosition,
                                          Duration refreshInterval) {
        int shardCount = queue.definition().shardCount();
        List<Future<Long>> committed = IntStream.range(0, shardCount)
                                                .mapToObj(shard -> client.findNextOffset(queue, consumerName, shard, startPosition))
                                                .toList();
        List<Future<Long>> starts = IntStream.range(0, shardCount)
                                             .mapToObj(shard -> committed.get(shard).compose(next -> startOffset(client, queue, shard,
                                                                                                                  next, startPosition)))
                                             .toList();
        return Future.all(starts).map(ignored -> {
            long[] committedNextOffsets = committed.stream().mapToLong(Future::result).toArray();
            long[] fetchOffsets = starts.stream().mapToLong(Future::result).toArray();
            DefaultQueueSubscription ret = new DefaultQueueSubscription(context, client, queue, consumerName,
                                                                        fetchOffsets, committedNextOffsets);
            context.runOnContext(v -> {
                ret.pullAll();
                ret.refreshTimer = context.owner().setPeriodic(refreshInterval.toMillis(), t -> ret.refreshCommitted());
            });
            return ret;
        });
    }

    // A committed next offset is at least one, so zero means the consumer never committed on the shard
    private static Future<Long> startOffset(QueueClusterClient client, StoredQueue queue, int shard, long committedNextOffset,
                                            StartPosition startPosition) {
        Future<Long> ret;
        if (committedNextOffset > 0 || startPosition == StartPosition.EARLIEST) {
            ret = Future.succeededFuture(committedNextOffset);
        } else {
            ret = client.findCommittedOffset(queue, shard);
        }
        return ret;
    }

    @Override
    public Future<Void> commit(QueueRecord record) {
        QueuePosition position = record.position();
        if (!queue.name().equals(position.queue())
                || position.shard() < 0
                || position.shard() >= deliveredNextOffsets.length
                || position.offset() >= deliveredNextOffsets[position.shard()]) {
            return Future.failedFuture(new IllegalArgumentException("Record " + position + " was not delivered by this subscription"));
        }
        Future<Void> ret;
        int shard = position.shard();
        long nextOffset = position.offset() + 1;
        if (nextOffset > committedNextOffsets[shard]) {
            // Raised only once stored, so a commit retried after a failure is sent again
            ret = client.commitOffset(queue, consumerName, shard, nextOffset)
                        .onSuccess(v -> committedNextOffsets[shard] = Math.max(committedNextOffsets[shard], nextOffset));
        } else {
            ret = Future.succeededFuture();
        }
        return ret;
    }

    @Override
    public Future<Void> close() {
        end();
        if (refreshTimer >= 0) {
            context().owner().cancelTimer(refreshTimer);
        }
        return Future.succeededFuture();
    }

    // Commits the committed positions again, so an open subscription that has nothing new to commit keeps them from
    // expiring; a failed refresh is made again at the next one
    private void refreshCommitted() {
        for (int shard = 0; shard < committedNextOffsets.length; shard++) {
            if (committedNextOffsets[shard] > 0) {
                int refreshed = shard;
                client.commitOffset(queue, consumerName, shard, committedNextOffsets[shard])
                      .onFailure(e -> log.debug("Refreshing the position of consumer {} on shard {} of queue {} failed",
                                                consumerName, refreshed, queue.name(), e));
            }
        }
    }

    @Override
    protected boolean canPull(int shard) {
        return pendingCount() < MAX_PENDING && pendingBytes + reservedBytes < MAX_PENDING_BYTES;
    }

    @Override
    protected Future<FetchResponse> pull(int shard) {
        long budget = Math.max(MIN_FETCH_BYTES, (MAX_PENDING_BYTES - pendingBytes - reservedBytes) / fetchOffsets.length);
        reservedBytes += budget;
        return client.fetch(queue, shard, fetchOffsets[shard], FETCH_SIZE, budget).onComplete(ar -> reservedBytes -= budget);
    }

    @Override
    protected void onPulled(int shard, FetchResponse response) {
        if (!isEnded()) {
            for (ShardEntry entry : response.entries()) {
                if (entry.isRecord()) {
                    pendingBytes += entry.size();
                    push(new QueueRecord(new QueuePosition(queue.name(), shard, entry.offset()), entry.key(), entry.payload()));
                }
            }
            fetchOffsets[shard] = response.nextOffset();
        }
    }

    @Override
    protected void onDelivered(QueueRecord record) {
        pendingBytes -= ShardEntry.size(record.key(), record.payload());
        deliveredNextOffsets[record.position().shard()] = record.position().offset() + 1;
    }
}
