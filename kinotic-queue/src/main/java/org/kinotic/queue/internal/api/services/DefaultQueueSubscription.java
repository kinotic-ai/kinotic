package org.kinotic.queue.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.services.QueueSubscription;
import org.kinotic.queue.internal.cluster.QueueClusterClient;
import org.kinotic.queue.internal.cluster.message.FetchResponse;
import org.kinotic.queue.internal.log.ShardEntry;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Delivers a queue's committed records to one consumer. Each shard is fetched from its owner, wherever the owner
 * runs, and the records are handed to the handler on the subscription's context as demand allows.
 */
public class DefaultQueueSubscription extends ShardPullStream<QueueRecord, FetchResponse> implements QueueSubscription {

    private static final int FETCH_SIZE = 256;
    // Fetching pauses while this many records wait for demand
    private static final int MAX_PENDING = 1_024;

    private final QueueClusterClient client;
    private final String queue;
    private final String consumerName;
    // The next offset to fetch per shard
    private final long[] fetchOffsets;
    private final long[] committedNextOffsets;
    // The offset after the last record handed to the handler, per shard; a commit may not go past it
    private final long[] deliveredNextOffsets;

    private DefaultQueueSubscription(Context context,
                                     QueueClusterClient client,
                                     String queue,
                                     String consumerName,
                                     long[] fetchOffsets,
                                     long[] committedNextOffsets) {
        super(context, fetchOffsets.length, "consumer " + consumerName + " of queue " + queue);
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
                                          QueueDefinition definition,
                                          String consumerName,
                                          StartPosition startPosition) {
        List<Future<Long>> committed = IntStream.range(0, definition.shardCount())
                                                .mapToObj(shard -> client.findNextOffset(definition.name(), consumerName, shard))
                                                .toList();
        List<Future<Long>> starts = IntStream.range(0, definition.shardCount())
                                             .mapToObj(shard -> committed.get(shard).compose(next -> startOffset(client, definition.name(), shard,
                                                                                                                  next, startPosition)))
                                             .toList();
        return Future.all(starts).map(ignored -> {
            long[] committedNextOffsets = committed.stream().mapToLong(Future::result).toArray();
            long[] fetchOffsets = starts.stream().mapToLong(Future::result).toArray();
            DefaultQueueSubscription ret = new DefaultQueueSubscription(context, client, definition.name(), consumerName,
                                                                        fetchOffsets, committedNextOffsets);
            context.runOnContext(v -> ret.pullAll());
            return ret;
        });
    }

    // A committed next offset is at least one, so zero means the consumer never committed on the shard
    private static Future<Long> startOffset(QueueClusterClient client, String queue, int shard, long committedNextOffset,
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
        if (!queue.equals(position.queue())
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
        return Future.succeededFuture();
    }

    @Override
    protected boolean canPull(int shard) {
        return pendingCount() < MAX_PENDING;
    }

    @Override
    protected Future<FetchResponse> pull(int shard) {
        return client.fetch(queue, shard, fetchOffsets[shard], FETCH_SIZE);
    }

    @Override
    protected void onPulled(int shard, FetchResponse response) {
        if (!isEnded()) {
            for (ShardEntry entry : response.entries()) {
                if (!entry.isMarker()) {
                    push(new QueueRecord(new QueuePosition(queue, shard, entry.offset()), entry.key(), entry.payload()));
                }
            }
            fetchOffsets[shard] = response.nextOffset();
        }
    }

    @Override
    protected void onDelivered(QueueRecord record) {
        deliveredNextOffsets[record.position().shard()] = record.position().offset() + 1;
    }
}
