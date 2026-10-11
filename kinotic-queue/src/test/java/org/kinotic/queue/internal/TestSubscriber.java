package org.kinotic.queue.internal;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.services.QueueService;
import org.kinotic.queue.api.services.QueueSubscription;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Drives a {@link QueueSubscription} from the test thread, calling it on the context it delivers on as its contract
 * requires, and collects the records it delivers.
 */
public final class TestSubscriber {

    private final Context context;
    private final QueueSubscription subscription;
    private final LinkedBlockingQueue<QueueRecord> received;
    private final LinkedBlockingQueue<Throwable> failures;

    private TestSubscriber(Context context,
                           QueueSubscription subscription,
                           LinkedBlockingQueue<QueueRecord> received,
                           LinkedBlockingQueue<Throwable> failures) {
        this.context = context;
        this.subscription = subscription;
        this.received = received;
        this.failures = failures;
    }

    public static TestSubscriber subscribe(Vertx vertx,
                                           QueueService service,
                                           String queue,
                                           String consumerName,
                                           StartPosition startPosition) throws Exception {
        Context context = vertx.getOrCreateContext();
        LinkedBlockingQueue<QueueRecord> received = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();
        CompletableFuture<QueueSubscription> subscribed = new CompletableFuture<>();
        context.runOnContext(v -> service.subscribe(queue, consumerName, startPosition)
                                         .onSuccess(subscription -> {
                                             subscription.exceptionHandler(failures::add);
                                             subscription.handler(received::add);
                                             subscribed.complete(subscription);
                                         })
                                         .onFailure(subscribed::completeExceptionally));
        return new TestSubscriber(context, subscribed.get(30, TimeUnit.SECONDS), received, failures);
    }

    /**
     * @return the next failure the subscription reported to its exception handler, or null when none came in time
     */
    public Throwable pollFailure(long millis) throws InterruptedException {
        return failures.poll(millis, TimeUnit.MILLISECONDS);
    }

    public QueueRecord next() throws InterruptedException {
        QueueRecord ret = received.poll(30, TimeUnit.SECONDS);
        assertNotNull(ret, "no record delivered within 30 seconds");
        return ret;
    }

    public QueueRecord poll(long millis) throws InterruptedException {
        return received.poll(millis, TimeUnit.MILLISECONDS);
    }

    public void onContext(Consumer<QueueSubscription> action) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        context.runOnContext(v -> {
            action.accept(subscription);
            done.complete(null);
        });
        done.get(30, TimeUnit.SECONDS);
    }

    public Future<Void> commit(QueueRecord record) throws Exception {
        CompletableFuture<Future<Void>> ret = new CompletableFuture<>();
        context.runOnContext(v -> ret.complete(subscription.commit(record)));
        return ret.get(30, TimeUnit.SECONDS);
    }

    public Future<Void> close() throws Exception {
        CompletableFuture<Future<Void>> ret = new CompletableFuture<>();
        context.runOnContext(v -> ret.complete(subscription.close()));
        return ret.get(30, TimeUnit.SECONDS);
    }
}
