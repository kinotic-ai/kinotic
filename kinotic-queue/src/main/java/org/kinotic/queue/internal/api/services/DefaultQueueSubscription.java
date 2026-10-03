package org.kinotic.queue.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.services.QueueSubscription;
import org.kinotic.queue.internal.cluster.QueueClusterClient;
import org.kinotic.queue.internal.cluster.message.FetchResponse;
import org.kinotic.queue.internal.log.ShardEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Delivers a queue's committed records to one consumer. Each shard is fetched from its owner, wherever the owner
 * runs, and the records are handed to the handler on the subscription's context as demand allows.
 */
@Slf4j
public class DefaultQueueSubscription implements QueueSubscription {

    private static final int FETCH_SIZE = 256;
    // Fetching pauses while this many records wait for demand
    private static final int MAX_PENDING = 1_024;
    private static final long RETRY_DELAY_MS = 200;

    private final Context context;
    private final QueueClusterClient client;
    private final String queue;
    private final String consumerName;
    // The next offset to fetch per shard; -1 until the owner resolves a LATEST start
    private final long[] fetchOffsets;
    private final boolean[] fetching;
    private final long[] committedNextOffsets;
    // The offset after the last record handed to the handler, per shard; a commit may not go past it
    private final long[] deliveredNextOffsets;
    private final ArrayDeque<QueueRecord> pending = new ArrayDeque<>();

    private Handler<QueueRecord> handler;
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> endHandler;
    private long demand = Long.MAX_VALUE;
    private boolean closed;

    private DefaultQueueSubscription(Context context,
                                     QueueClusterClient client,
                                     String queue,
                                     String consumerName,
                                     long[] fetchOffsets,
                                     long[] committedNextOffsets) {
        this.context = context;
        this.client = client;
        this.queue = queue;
        this.consumerName = consumerName;
        this.fetchOffsets = fetchOffsets;
        this.fetching = new boolean[fetchOffsets.length];
        this.committedNextOffsets = committedNextOffsets;
        this.deliveredNextOffsets = new long[fetchOffsets.length];
        for (int i = 0; i < fetchOffsets.length; i++) {
            deliveredNextOffsets[i] = Math.max(0, fetchOffsets[i]);
        }
    }

    /**
     * Opens a subscription positioned after the consumer's committed offsets.
     */
    static Future<QueueSubscription> open(Context context,
                                          QueueClusterClient client,
                                          QueueDefinition definition,
                                          String consumerName,
                                          StartPosition startPosition) {
        List<Future<Long>> committed = IntStream.range(0, definition.shardCount())
                                                .mapToObj(shard -> client.findNextOffset(definition.name(), consumerName, shard))
                                                .toList();
        return Future.all(committed).map(ignored -> {
            long[] committedNextOffsets = new long[committed.size()];
            long[] fetchOffsets = new long[committed.size()];
            for (int i = 0; i < committed.size(); i++) {
                committedNextOffsets[i] = committed.get(i).result();
                if (committedNextOffsets[i] > 0) {
                    fetchOffsets[i] = committedNextOffsets[i];
                } else {
                    fetchOffsets[i] = startPosition == StartPosition.EARLIEST ? 0 : -1;
                }
            }
            DefaultQueueSubscription ret = new DefaultQueueSubscription(context, client, definition.name(), consumerName,
                                                                        fetchOffsets, committedNextOffsets);
            context.runOnContext(v -> ret.fetchAll());
            return ret;
        });
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
            committedNextOffsets[shard] = nextOffset;
            ret = client.commitOffset(queue, consumerName, shard, nextOffset);
        } else {
            ret = Future.succeededFuture();
        }
        return ret;
    }

    @Override
    public Future<Void> close() {
        if (!closed) {
            closed = true;
            pending.clear();
            if (endHandler != null) {
                endHandler.handle(null);
            }
        }
        return Future.succeededFuture();
    }

    @Override
    public QueueSubscription exceptionHandler(Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    @Override
    public QueueSubscription handler(Handler<QueueRecord> handler) {
        this.handler = handler;
        deliver();
        return this;
    }

    @Override
    public QueueSubscription pause() {
        demand = 0;
        return this;
    }

    @Override
    public QueueSubscription resume() {
        demand = Long.MAX_VALUE;
        deliver();
        return this;
    }

    @Override
    public QueueSubscription fetch(long amount) {
        demand += amount;
        if (demand < 0) {
            demand = Long.MAX_VALUE;
        }
        deliver();
        return this;
    }

    @Override
    public QueueSubscription endHandler(Handler<Void> endHandler) {
        this.endHandler = endHandler;
        return this;
    }

    private void fetchAll() {
        for (int shard = 0; shard < fetchOffsets.length; shard++) {
            fetchShard(shard);
        }
    }

    private void fetchShard(int shard) {
        if (!closed && !fetching[shard] && pending.size() < MAX_PENDING) {
            fetching[shard] = true;
            client.fetch(queue, shard, fetchOffsets[shard], FETCH_SIZE).onComplete(ar -> {
                if (ar.succeeded()) {
                    fetching[shard] = false;
                    onFetched(shard, ar.result());
                    fetchShard(shard);
                } else {
                    // The shard is between owners; the next fetch looks its owner up again
                    log.debug("Fetching shard {} of queue {} failed, retrying", shard, queue, ar.cause());
                    // The shard stays marked as fetching until the retry, so deliver() cannot start a second fetch loop
                    context.owner().timer(RETRY_DELAY_MS, TimeUnit.MILLISECONDS).onComplete(t -> {
                        fetching[shard] = false;
                        fetchShard(shard);
                    });
                }
            });
        }
    }

    private void onFetched(int shard, FetchResponse response) {
        if (!closed) {
            for (ShardEntry entry : response.entries()) {
                pending.add(new QueueRecord(new QueuePosition(queue, shard, entry.offset()), entry.key(), entry.payload()));
            }
            fetchOffsets[shard] = response.nextOffset();
            if (deliveredNextOffsets[shard] == 0 && response.entries().isEmpty()) {
                // A LATEST start learns its offset from the owner
                deliveredNextOffsets[shard] = response.nextOffset();
            }
            deliver();
        }
    }

    private void deliver() {
        while (!closed && handler != null && demand > 0 && !pending.isEmpty()) {
            QueueRecord record = pending.poll();
            if (demand != Long.MAX_VALUE) {
                demand--;
            }
            QueuePosition position = record.position();
            deliveredNextOffsets[position.shard()] = position.offset() + 1;
            try {
                handler.handle(record);
            } catch (Throwable t) {
                fail(t);
            }
        }
        if (!closed && pending.size() < MAX_PENDING) {
            fetchAll();
        }
    }

    private void fail(Throwable t) {
        if (exceptionHandler != null) {
            exceptionHandler.handle(t);
        } else {
            log.error("Subscription of consumer {} to queue {} failed", consumerName, queue, t);
        }
        close();
    }
}
