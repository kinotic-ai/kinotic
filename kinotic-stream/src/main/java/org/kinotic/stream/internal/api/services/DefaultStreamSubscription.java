package org.kinotic.stream.internal.api.services;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.stream.api.model.StartPosition;
import org.kinotic.stream.api.model.StreamPosition;
import org.kinotic.stream.api.model.StreamRecord;
import org.kinotic.stream.api.services.StreamSubscription;
import org.kinotic.stream.internal.log.ShardLog;
import org.kinotic.stream.internal.log.ShardReader;
import org.kinotic.stream.internal.log.StreamLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Delivers a stream's records to one consumer, reading shards on worker threads and handing records to the
 * handler on the subscription's context as demand allows.
 */
@Slf4j
public class DefaultStreamSubscription implements StreamSubscription {

    private static final int MAX_BATCH_SIZE = 256;

    private final Context context;
    private final StreamLog streamLog;
    private final String consumerName;
    private final ShardReader[] readers;
    private final long[] committedNextOffsets;
    // The offset after the last record handed to the handler, per shard; a commit may not go past it
    private final long[] deliveredNextOffsets;
    private final ArrayDeque<StreamRecord> pending = new ArrayDeque<>();
    // Set by appends while a read is in flight, so a read that found nothing runs again instead of missing them
    private final AtomicBoolean appended = new AtomicBoolean();
    private final Runnable appendListener = this::onAppend;
    private final Promise<Void> closePromise = Promise.promise();

    private Handler<StreamRecord> handler;
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> endHandler;
    private long demand = Long.MAX_VALUE;
    private boolean reading;
    private boolean closed;
    // Read only by readBatch, which never runs concurrently with itself
    private int firstShardToRead;

    private DefaultStreamSubscription(Context context,
                                      StreamLog streamLog,
                                      String consumerName,
                                      ShardReader[] readers,
                                      long[] committedNextOffsets,
                                      long[] startOffsets) {
        this.context = context;
        this.streamLog = streamLog;
        this.consumerName = consumerName;
        this.readers = readers;
        this.committedNextOffsets = committedNextOffsets;
        this.deliveredNextOffsets = startOffsets;
        for (int i = 0; i < readers.length; i++) {
            streamLog.shard(i).addAppendListener(appendListener);
        }
    }

    /**
     * Opens a subscription positioned after the consumer's committed offsets. Blocks on disk reads.
     */
    static DefaultStreamSubscription open(Context context,
                                          StreamLog streamLog,
                                          String consumerName,
                                          StartPosition startPosition) {
        int shardCount = streamLog.definition().shardCount();
        long[] committedNextOffsets = streamLog.consumerOffsets().findNextOffsets(consumerName);
        long[] startOffsets = new long[shardCount];
        ShardReader[] readers = new ShardReader[shardCount];
        try {
            for (int i = 0; i < shardCount; i++) {
                ShardLog shard = streamLog.shard(i);
                if (committedNextOffsets[i] > 0) {
                    startOffsets[i] = committedNextOffsets[i];
                } else if (startPosition == StartPosition.EARLIEST) {
                    startOffsets[i] = 0;
                } else {
                    startOffsets[i] = shard.nextOffset();
                }
                readers[i] = shard.newReader(startOffsets[i]);
            }
        } catch (RuntimeException e) {
            for (ShardReader reader : readers) {
                if (reader != null) {
                    reader.close();
                }
            }
            throw e;
        }
        return new DefaultStreamSubscription(context, streamLog, consumerName, readers, committedNextOffsets, startOffsets);
    }

    @Override
    public Future<Void> commit(StreamRecord record) {
        StreamPosition position = record.position();
        if (!streamLog.definition().name().equals(position.stream())
                || position.shard() < 0
                || position.shard() >= readers.length
                || position.offset() >= deliveredNextOffsets[position.shard()]) {
            return Future.failedFuture(new IllegalArgumentException("Record " + position + " was not delivered by this subscription"));
        }
        Future<Void> ret;
        int shard = position.shard();
        long nextOffset = position.offset() + 1;
        if (nextOffset > committedNextOffsets[shard]) {
            committedNextOffsets[shard] = nextOffset;
            // Ordered so a later commit can never be overwritten by an earlier one still in flight
            ret = context.executeBlocking(() -> {
                streamLog.consumerOffsets().save(consumerName, shard, nextOffset);
                return null;
            }, true);
        } else {
            ret = Future.succeededFuture();
        }
        return ret;
    }

    @Override
    public Future<Void> close() {
        if (!closed) {
            closed = true;
            for (int i = 0; i < readers.length; i++) {
                streamLog.shard(i).removeAppendListener(appendListener);
            }
            pending.clear();
            // A read in flight still holds the readers; onBatch closes them when it returns
            if (!reading) {
                closeReaders();
            }
            if (endHandler != null) {
                endHandler.handle(null);
            }
        }
        return closePromise.future();
    }

    @Override
    public StreamSubscription exceptionHandler(Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    @Override
    public StreamSubscription handler(Handler<StreamRecord> handler) {
        this.handler = handler;
        drain();
        return this;
    }

    @Override
    public StreamSubscription pause() {
        demand = 0;
        return this;
    }

    @Override
    public StreamSubscription resume() {
        demand = Long.MAX_VALUE;
        drain();
        return this;
    }

    @Override
    public StreamSubscription fetch(long amount) {
        demand += amount;
        if (demand < 0) {
            demand = Long.MAX_VALUE;
        }
        drain();
        return this;
    }

    @Override
    public StreamSubscription endHandler(Handler<Void> endHandler) {
        this.endHandler = endHandler;
        return this;
    }

    // Runs on the appending thread
    private void onAppend() {
        if (appended.compareAndSet(false, true)) {
            context.runOnContext(v -> drain());
        }
    }

    private void drain() {
        deliverPending();
        if (!closed && !reading && handler != null && demand > 0 && pending.isEmpty()) {
            reading = true;
            appended.set(false);
            int max = (int) Math.min(demand, MAX_BATCH_SIZE);
            context.executeBlocking(() -> readBatch(max), false)
                   .onComplete(this::onBatch);
        }
    }

    private void onBatch(AsyncResult<List<StreamRecord>> result) {
        reading = false;
        if (closed) {
            closeReaders();
        } else if (result.failed()) {
            fail(result.cause());
        } else {
            pending.addAll(result.result());
            if (!result.result().isEmpty() || appended.get()) {
                drain();
            }
        }
    }

    private List<StreamRecord> readBatch(int max) {
        List<StreamRecord> ret = new ArrayList<>();
        // Rotates the first shard read so a busy shard cannot starve the others
        for (int i = 0; i < readers.length && ret.size() < max; i++) {
            ret.addAll(readers[(firstShardToRead + i) % readers.length].read(max - ret.size()));
        }
        firstShardToRead = (firstShardToRead + 1) % readers.length;
        return ret;
    }

    private void deliverPending() {
        while (!closed && handler != null && demand > 0 && !pending.isEmpty()) {
            StreamRecord record = pending.poll();
            if (demand != Long.MAX_VALUE) {
                demand--;
            }
            StreamPosition position = record.position();
            deliveredNextOffsets[position.shard()] = position.offset() + 1;
            try {
                handler.handle(record);
            } catch (Throwable t) {
                fail(t);
            }
        }
    }

    private void fail(Throwable t) {
        if (exceptionHandler != null) {
            exceptionHandler.handle(t);
        } else {
            log.error("Subscription of consumer {} to stream {} failed", consumerName, streamLog.definition().name(), t);
        }
        close();
    }

    private void closeReaders() {
        for (ShardReader reader : readers) {
            reader.close();
        }
        closePromise.tryComplete();
    }
}
