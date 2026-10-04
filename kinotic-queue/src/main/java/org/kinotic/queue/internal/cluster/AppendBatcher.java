package org.kinotic.queue.internal.cluster;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.kinotic.queue.internal.cluster.message.AppendRecord;
import org.kinotic.queue.internal.cluster.message.AppendRequest;
import org.kinotic.queue.internal.log.ShardEntry;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Sends one client's appends to each shard in batches, one batch per shard at a time and in the order the appends
 * were made. Each batch carries the client's producer id and a sequence that grows with every batch to the shard, so
 * a batch sent again after a failure is written once. Runs on its own context.
 */
final class AppendBatcher {

    private static final int MAX_BATCH_RECORDS = 1_000;
    private static final long MAX_BATCH_BYTES = 1024 * 1024;

    private final Context context;
    private final Function<AppendRequest, Future<List<Long>>> send;
    // Random, so clients that run at the same time, or one after another, never share an id
    private final long producerId = new SecureRandom().nextLong();
    private final Map<String, ShardAppendQueue> queues = new HashMap<>();

    /**
     * @param send sends a batch to the shard's owner, retrying while the shard has no owner, and answers with the
     *             offset of each record
     */
    AppendBatcher(Vertx vertx, Function<AppendRequest, Future<List<Long>>> send) {
        this.context = vertx.getOrCreateContext();
        this.send = send;
    }

    /**
     * Appends a record in the shard's next batch.
     *
     * @return the record's offset, completed on the calling context
     */
    Future<Long> append(String queue, int shard, String key, byte[] payload) {
        Promise<Long> promise = Promise.promise();
        QueuedAppend append = new QueuedAppend(key, payload, promise, Vertx.currentContext());
        context.runOnContext(v -> {
            ShardAppendQueue shardQueue = queues.computeIfAbsent(queue + "/" + shard, name -> new ShardAppendQueue(queue, shard));
            shardQueue.getWaiting().add(append);
            sendNext(shardQueue);
        });
        return promise.future();
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
            send.apply(new AppendRequest(shardQueue.getQueue(), shardQueue.getShard(), producerId, sequence, records))
                .onComplete(ar -> {
                    shardQueue.setInFlight(false);
                    for (int i = 0; i < batch.size(); i++) {
                        complete(batch.get(i), ar, i);
                    }
                    sendNext(shardQueue);
                });
        }
    }

    private static void complete(QueuedAppend append, AsyncResult<List<Long>> result, int index) {
        Runnable completion = () -> {
            if (result.succeeded()) {
                append.promise().complete(result.result().get(index));
            } else {
                append.promise().fail(result.cause());
            }
        };
        if (append.caller() != null) {
            append.caller().runOnContext(v -> completion.run());
        } else {
            completion.run();
        }
    }
}
