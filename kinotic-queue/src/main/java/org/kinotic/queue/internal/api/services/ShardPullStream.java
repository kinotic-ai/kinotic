package org.kinotic.queue.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.internal.cluster.QueueFailure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ReadStream} that pulls items from every shard of a queue, one request per shard at a time, and hands them
 * to the handler as demand allows. A shard whose request fails is pulled again after a short delay, which also finds
 * its new owner when it changed; a shard that keeps failing for {@link #FAILURE_REPORT_MS} is reported to the exception
 * handler once, and pulling it goes on. A stream whose queue was deleted reports it to the exception handler and closes.
 * Runs on the context it was created for.
 *
 * @param <T> the items the stream delivers
 * @param <R> what one pull from a shard returns
 */
@Slf4j
abstract class ShardPullStream<T, R> implements ReadStream<T> {

    private static final long RETRY_DELAY_MS = 200;

    /**
     * How long a shard keeps failing before the failure is reported.
     */
    static final long FAILURE_REPORT_MS = 30_000;

    private final Context context;
    private final String description;
    private final boolean[] pulling;
    // When each shard's current run of failed pulls began, or 0 while its last pull succeeded
    private final long[] failingSince;
    private final boolean[] failureReported;
    private final ArrayDeque<T> pending = new ArrayDeque<>();

    private Handler<T> handler;
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> endHandler;
    private long demand = Long.MAX_VALUE;
    private boolean closed;
    private boolean delivering;

    /**
     * @param description names the stream in log messages
     */
    ShardPullStream(Context context, int shardCount, String description) {
        this.context = context;
        this.description = description;
        this.pulling = new boolean[shardCount];
        this.failingSince = new long[shardCount];
        this.failureReported = new boolean[shardCount];
    }

    /**
     * @return whether the shard may be pulled now
     */
    protected abstract boolean canPull(int shard);

    protected abstract Future<R> pull(int shard);

    /**
     * Handles a pull's result, {@link #push pushing} its items. Called after the stream ended too, for results that
     * were on their way.
     */
    protected abstract void onPulled(int shard, R result);

    /**
     * Called just before an item is handed to the handler.
     */
    protected abstract void onDelivered(T item);

    /**
     * Stops the stream, also when its handler throws.
     */
    public abstract Future<Void> close();

    @Override
    public final ReadStream<T> exceptionHandler(Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    @Override
    public final ReadStream<T> handler(Handler<T> handler) {
        this.handler = handler;
        deliver();
        return this;
    }

    @Override
    public final ReadStream<T> pause() {
        demand = 0;
        return this;
    }

    @Override
    public final ReadStream<T> resume() {
        demand = Long.MAX_VALUE;
        deliver();
        return this;
    }

    @Override
    public final ReadStream<T> fetch(long amount) {
        demand += amount;
        if (demand < 0) {
            demand = Long.MAX_VALUE;
        }
        deliver();
        return this;
    }

    @Override
    public final ReadStream<T> endHandler(Handler<Void> endHandler) {
        this.endHandler = endHandler;
        return this;
    }

    protected final void push(T item) {
        pending.add(item);
    }

    /**
     * @return the context the stream runs on
     */
    protected final Context context() {
        return context;
    }

    protected final int pendingCount() {
        return pending.size();
    }

    protected final boolean isEnded() {
        return closed;
    }

    /**
     * Pulls every shard that is not being pulled and {@link #canPull may be}.
     */
    protected final void pullAll() {
        for (int shard = 0; shard < pulling.length; shard++) {
            pullShard(shard);
        }
    }

    /**
     * Hands pending items to the handler as demand allows, then pulls more.
     */
    protected final void deliver() {
        // A handler calling fetch() or resume() lands here again; the loop below picks up the demand it added
        if (delivering) {
            return;
        }
        delivering = true;
        try {
            while (!closed && handler != null && demand > 0 && !pending.isEmpty()) {
                T item = pending.poll();
                if (demand != Long.MAX_VALUE) {
                    demand--;
                }
                onDelivered(item);
                try {
                    handler.handle(item);
                } catch (Throwable t) {
                    fail(t);
                }
            }
        } finally {
            delivering = false;
        }
        if (!closed) {
            pullAll();
        }
    }

    /**
     * Stops delivery and calls the end handler.
     *
     * @return the items pulled but never handed to the handler; empty when the stream had already ended
     */
    protected final List<T> end() {
        List<T> ret = new ArrayList<>();
        if (!closed) {
            closed = true;
            ret.addAll(pending);
            pending.clear();
            if (endHandler != null) {
                endHandler.handle(null);
            }
        }
        return ret;
    }

    private void pullShard(int shard) {
        if (!closed && !pulling[shard] && canPull(shard)) {
            pulling[shard] = true;
            pull(shard).onComplete(ar -> {
                if (ar.succeeded()) {
                    pulling[shard] = false;
                    failingSince[shard] = 0;
                    failureReported[shard] = false;
                    onPulled(shard, ar.result());
                    deliver();
                } else if (!closed && QueueFailure.NO_QUEUE.matches(ar.cause())) {
                    fail(new IllegalStateException("The queue of " + description + " was deleted", ar.cause()));
                } else {
                    log.debug("Pulling shard {} for {} failed, retrying", shard, description, ar.cause());
                    // The shard stays marked as pulling until the retry, so deliver() cannot start a second pull of it
                    context.owner().timer(RETRY_DELAY_MS, TimeUnit.MILLISECONDS).onComplete(t -> {
                        pulling[shard] = false;
                        pullShard(shard);
                    });
                    reportPersistentFailure(shard, ar.cause());
                }
            });
        }
    }

    private void reportPersistentFailure(int shard, Throwable cause) {
        long now = System.currentTimeMillis();
        if (failingSince[shard] == 0) {
            failingSince[shard] = now;
        }
        if (!failureReported[shard] && now - failingSince[shard] >= FAILURE_REPORT_MS) {
            failureReported[shard] = true;
            IllegalStateException failure = new IllegalStateException("Shard " + shard + " of " + description + " could not be read for "
                                                                              + (now - failingSince[shard]) + " ms; still retrying", cause);
            log.warn(failure.getMessage(), cause);
            notifyExceptionHandler(failure);
        }
    }

    private void fail(Throwable t) {
        if (exceptionHandler != null) {
            notifyExceptionHandler(t);
        } else {
            log.error("{} stopped", description, t);
        }
        close();
    }

    private void notifyExceptionHandler(Throwable t) {
        if (exceptionHandler != null) {
            try {
                exceptionHandler.handle(t);
            } catch (Throwable thrown) {
                log.error("The exception handler of {} failed", description, thrown);
            }
        }
    }
}
