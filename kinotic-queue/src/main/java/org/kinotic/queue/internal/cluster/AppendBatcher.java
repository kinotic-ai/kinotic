package org.kinotic.queue.internal.cluster;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.internal.cluster.message.AppendRecord;
import org.kinotic.queue.internal.cluster.message.AppendRequest;
import org.kinotic.queue.internal.cluster.message.ShardId;
import org.kinotic.queue.internal.log.QueueLog;
import org.kinotic.queue.internal.log.ShardEntry;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Sends one client's appends to each shard in batches, one batch per shard at a time and in the order the appends
 * were made, also while a queue's definition is still being looked up. Each batch carries a producer id the client
 * draws for the shard and a sequence that grows with every batch it sends the shard, so a batch sent again after a
 * failure is written once. A shard the client sent nothing to for {@link #IDLE_MS} is forgotten, and the client's later
 * appends to it come from a new producer id. Runs on its own context.
 */
final class AppendBatcher {

    private static final int MAX_BATCH_RECORDS = 1_000;
    private static final long MAX_BATCH_BYTES = 1024 * 1024;
    // Appends to a shard with no owner wait up to the client's retry deadline; past this many bytes waiting for the
    // shard, an append fails at once rather than holding more memory
    private static final long MAX_WAITING_BYTES = 64L * 1024 * 1024;
    private static final long IDLE_MS = 10 * 60_000;
    // Random, so clients that run at the same time, or one after another, never share a producer id
    private static final SecureRandom PRODUCER_IDS = new SecureRandom();

    private final Context context;
    private final Function<AppendRequest, Future<List<Long>>> send;
    private final Map<ShardId, ShardAppendQueue> queues = new HashMap<>();
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
                ShardAppendQueue shardQueue = shardQueue(stored.shardId(shard));
                long size = ShardEntry.size(append.key(), append.payload());
                if (shardQueue.getWaitingBytes() + size > MAX_WAITING_BYTES) {
                    complete(List.of(append), index -> Future.failedFuture(new IllegalStateException(
                            "Appends to " + shardQueue.getShardId() + " waiting to be sent already take " + shardQueue.getWaitingBytes()
                                    + " bytes; the shard may have no owner")));
                } else {
                    shardQueue.getWaiting().add(append);
                    shardQueue.setWaitingBytes(shardQueue.getWaitingBytes() + size);
                    sendNext(shardQueue);
                }
            } else {
                complete(List.of(append), index -> Future.failedFuture(append.queue().cause()));
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

    // A queue the client sent nothing to for IDLE_MS is dropped when another is created, so the shards of deleted
    // queues are forgotten. A dropped queue's sequence starts over under a new producer id, which a shard's owner never
    // takes for an earlier batch.
    private ShardAppendQueue shardQueue(ShardId shardId) {
        ShardAppendQueue ret = queues.get(shardId);
        if (ret == null) {
            long idleSince = MonotonicClock.millis() - IDLE_MS;
            queues.values().removeIf(idle -> !idle.isInFlight() && idle.getWaiting().isEmpty() && idle.getLastUsed() < idleSince);
            ret = new ShardAppendQueue(shardId, PRODUCER_IDS.nextLong());
            queues.put(shardId, ret);
        }
        return ret;
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
            shardQueue.setWaitingBytes(shardQueue.getWaitingBytes() - bytes);
            long sequence = shardQueue.getNextSequence();
            shardQueue.setNextSequence(sequence + 1);
            shardQueue.setInFlight(true);
            shardQueue.setLastUsed(MonotonicClock.millis());
            List<AppendRecord> records = batch.stream().map(append -> new AppendRecord(append.key(), append.payload())).toList();
            ShardId shardId = shardQueue.getShardId();
            send.apply(new AppendRequest(shardId, shardQueue.getProducerId(), sequence, records))
                .onComplete(ar -> {
                    shardQueue.setInFlight(false);
                    complete(batch, index -> ar.map(offsets -> new QueuePosition(shardId.queue(), shardId.shard(), offsets.get(index))));
                    sendNext(shardQueue);
                });
        }
    }

    // Completes each append with its result, on its caller's context, with one task per context
    private static void complete(List<QueuedAppend> appends, IntFunction<AsyncResult<QueuePosition>> result) {
        Map<Context, List<Runnable>> byCaller = new LinkedHashMap<>();
        for (int i = 0; i < appends.size(); i++) {
            QueuedAppend append = appends.get(i);
            AsyncResult<QueuePosition> appended = result.apply(i);
            Runnable completion = () -> append.promise().handle(appended);
            if (append.caller() != null) {
                byCaller.computeIfAbsent(append.caller(), caller -> new ArrayList<>()).add(completion);
            } else {
                completion.run();
            }
        }
        byCaller.forEach((caller, completions) -> caller.runOnContext(v -> completions.forEach(Runnable::run)));
    }
}
