package org.kinotic.queue.internal;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.streams.ReadStream;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Drives a queue's {@link ReadStream} from the test thread, calling it on the context it delivers on as its contract
 * requires, and collects what it delivers and the failures it reports.
 *
 * @param <S> the stream
 * @param <T> what the stream delivers
 */
abstract class TestStream<S extends ReadStream<T>, T> {

    private final Context context;
    private final S stream;
    private final LinkedBlockingQueue<T> received = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();

    /**
     * @param open opens the stream, on the context it then delivers on
     */
    TestStream(Vertx vertx, Supplier<Future<S>> open) throws Exception {
        context = vertx.getOrCreateContext();
        Promise<S> opened = Promise.promise();
        context.runOnContext(v -> open.get()
                                      .onSuccess(stream -> {
                                          stream.exceptionHandler(failures::add);
                                          stream.handler(received::add);
                                      })
                                      .onComplete(opened));
        stream = opened.future().await(30, TimeUnit.SECONDS);
    }

    /**
     * @return the next failure the stream reported to its exception handler, or null when none came in time
     */
    public Throwable pollFailure(long millis) throws InterruptedException {
        return failures.poll(millis, TimeUnit.MILLISECONDS);
    }

    public T next() throws InterruptedException {
        T ret = received.poll(30, TimeUnit.SECONDS);
        assertNotNull(ret, "nothing delivered within 30 seconds");
        return ret;
    }

    public T poll(long millis) throws InterruptedException {
        return received.poll(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Runs the action on the stream's context, and waits until it ran.
     */
    public void onContext(Consumer<S> action) throws Exception {
        call(stream -> {
            action.accept(stream);
            return Future.succeededFuture();
        });
    }

    /**
     * @return what the call returns, called on the stream's context
     */
    <R> Future<R> call(Function<S, Future<R>> call) throws Exception {
        Promise<Future<R>> ret = Promise.promise();
        context.runOnContext(v -> ret.complete(call.apply(stream)));
        return ret.future().await(30, TimeUnit.SECONDS);
    }
}
