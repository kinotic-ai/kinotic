package org.kinotic.queue.internal.cluster;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.internal.cluster.message.AppendRecord;
import org.kinotic.queue.internal.cluster.message.AppendRequest;
import org.kinotic.queue.internal.log.QueueLog;
import org.kinotic.queue.internal.log.ShardEntry;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Sends one client's appends to each shard in batches, one batch per shard at a time and in the order the appends
 * were made, also while a queue's definition is still being looked up. Each batch carries the client's producer id and
 * a sequence that grows with every batch to the shard, so a batch sent again after a failure is written once. Runs on
 * its own context.
 */
final class AppendBatcher {

    private static final int MAX_BATCH_RECORDS = 1_000;
    private static final long MAX_BATCH_BYTES = 1024 * 1024;

    private final Context context;
    private final Function<AppendRequest, Future<List<Long>>> send;
    // Random, so clients that run at the same time, or one after another, never share an id
    private final long producerId = new SecureRandom().nextLong();
    private final Map<String, ShardAppendQueue> queues = new HashMap<>();
    // Each queue's appends not yet given to a shard, in the order they were made, while the first one's definition is
    // still being looked up
    private final Map<String, ArrayDeque<QueuedAppend>> resolving = new HashMap<>();
    // The queues whose first resolving append has a callback waiting on its definition
    private final Set<String> awaitingDefinition = new HashSet<>();

    /**
     * @param send sends a batch to the shard's owner, retrying while the shard has no owner, and answers with the
     *             offset of each record
     */
    AppendBatcher(Vertx vertx, Function<AppendRequest, Future<List<Long>>> send) {
        this.context = vertx.getOrCreateContext();
        this.send = send;
    }

    /**
     * Appends a record in the next batch of the shard its key hashes to.
     *
     * @param stored the queue as the cluster stores it, or its lookup; the append fails as the lookup does
     * @return the record's position, completed on the calling context
     */
    Future<QueuePosition> append(String queue, Future<StoredQueue> stored, String key, byte[] payload) {
        Promise<QueuePosition> promise = Promise.promise();
        QueuedAppend append = new QueuedAppend(stored, key, payload, promise, Vertx.currentContext());
        context.runOnContext(v -> {
            resolving.computeIfAbsent(queue, name -> new ArrayDeque<>()).add(append);
            resolve(queue);
        });
        return promise.future();
    }

    // Gives the queue's appends to their shards in the order they were made, up to the first whose definition is not
    // known yet, and resumes once it is
    private void resolve(String queue) {
        ArrayDeque<QueuedAppend> appends = resolving.get(queue);
        while (!appends.isEmpty() && appends.peek().queue().isComplete()) {
            QueuedAppend append = appends.poll();
            if (append.queue().succeeded()) {
                StoredQueue stored = append.queue().result();
                int shard = QueueLog.shardOf(append.key(), stored.definition().shardCount());
                ShardAppendQueue shardQueue = queues.computeIfAbsent(stored.incarnation() + "/" + shard,
                                                                     name -> new ShardAppendQueue(queue, stored.incarnation(), shard));
                shardQueue.getWaiting().add(append);
                sendNext(shardQueue);
            } else {
                complete(append, Future.failedFuture(append.queue().cause()));
            }
        }
        if (appends.isEmpty()) {
            resolving.remove(queue);
        } else if (awaitingDefinition.add(queue)) {
            appends.peek().queue().onComplete(ar -> context.runOnContext(v -> {
                awaitingDefinition.remove(queue);
                resolve(queue);
            }));
        }
    }

    private void sendNext(ShardAppendQueue shardQueue) {
        if (!shardQueue.isInFlight() && !shardQueue.getWaiting().isEmpty()) {
            List<QueuedAppend> batch = new ArrayList<>();
            long bytes = 0;
            while (!shardQueue.getWaiting().isEmpty() && batch.size() < MAX_BATCH_RECORDS && (batch.isEmpty() || bytes < MAX_BATCH_BYTES)) {
                QueuedAppend append = shardQueue.getWaiting().poll();
                batch.add(append);
                bytes += ShardEntry.size(append.key(), append.payload());
            }
            long sequence = shardQueue.getNextSequence();
            shardQueue.setNextSequence(sequence + 1);
            shardQueue.setInFlight(true);
            List<AppendRecord> records = batch.stream().map(append -> new AppendRecord(append.key(), append.payload())).toList();
            send.apply(new AppendRequest(shardQueue.getQueue(), shardQueue.getIncarnation(), shardQueue.getShard(), producerId, sequence, records))
                .onComplete(ar -> {
                    shardQueue.setInFlight(false);
                    for (int i = 0; i < batch.size(); i++) {
                        int index = i;
                        complete(batch.get(i), ar.map(offsets -> new QueuePosition(shardQueue.getQueue(), shardQueue.getShard(),
                                                                                   offsets.get(index))));
                    }
                    sendNext(shardQueue);
                });
        }
    }

    private static void complete(QueuedAppend append, AsyncResult<QueuePosition> result) {
        Runnable completion = () -> append.promise().handle(result);
        if (append.caller() != null) {
            append.caller().runOnContext(v -> completion.run());
        } else {
            completion.run();
        }
    }
}
