package org.kinotic.queue.internal;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.queue.api.model.WorkItem;
import org.kinotic.queue.api.model.WorkerOptions;
import org.kinotic.queue.api.services.QueueService;
import org.kinotic.queue.api.services.QueueWorker;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Drives a {@link QueueWorker} from the test thread, calling it on the context it delivers on as its contract
 * requires, and collects the items leased to it.
 */
public final class TestWorker {

    private final Context context;
    private final QueueWorker worker;
    private final LinkedBlockingQueue<WorkItem> received;

    private TestWorker(Context context, QueueWorker worker, LinkedBlockingQueue<WorkItem> received) {
        this.context = context;
        this.worker = worker;
        this.received = received;
    }

    public static TestWorker start(Vertx vertx,
                                   QueueService service,
                                   String queue,
                                   String groupName,
                                   WorkerOptions options) throws Exception {
        Context context = vertx.getOrCreateContext();
        LinkedBlockingQueue<WorkItem> received = new LinkedBlockingQueue<>();
        CompletableFuture<QueueWorker> started = new CompletableFuture<>();
        context.runOnContext(v -> service.work(queue, groupName, options)
                                         .onSuccess(worker -> {
                                             worker.handler(received::add);
                                             started.complete(worker);
                                         })
                                         .onFailure(started::completeExceptionally));
        return new TestWorker(context, started.get(30, TimeUnit.SECONDS), received);
    }

    public static int value(WorkItem item) {
        return QueueTestSupport.value(item.record());
    }

    public WorkItem next() throws InterruptedException {
        WorkItem ret = received.poll(30, TimeUnit.SECONDS);
        assertNotNull(ret, "no record leased within 30 seconds");
        return ret;
    }

    public WorkItem poll(long millis) throws InterruptedException {
        return received.poll(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * @return the values leased until none arrives for a while
     */
    public Set<Integer> takeUntilQuiet() throws InterruptedException {
        Set<Integer> ret = new HashSet<>();
        WorkItem item = received.poll(30, TimeUnit.SECONDS);
        while (item != null) {
            ret.add(value(item));
            item = received.poll(1_500, TimeUnit.MILLISECONDS);
        }
        return ret;
    }

    public Future<Void> settle(BiFunction<QueueWorker, WorkItem, Future<Void>> settlement, WorkItem item) throws Exception {
        CompletableFuture<Future<Void>> ret = new CompletableFuture<>();
        context.runOnContext(v -> ret.complete(settlement.apply(worker, item)));
        return ret.get(30, TimeUnit.SECONDS);
    }

    public Future<Void> close() throws Exception {
        CompletableFuture<Future<Void>> ret = new CompletableFuture<>();
        context.runOnContext(v -> ret.complete(worker.close()));
        return ret.get(30, TimeUnit.SECONDS);
    }
}
