package org.kinotic.queue.internal;

import io.vertx.core.Future;
import org.kinotic.queue.api.model.WorkItem;
import org.kinotic.queue.api.model.WorkerOptions;
import org.kinotic.queue.api.services.QueueWorker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Drives a {@link QueueWorker} from the test thread and collects the items leased to it.
 */
public final class TestWorker extends TestStream<QueueWorker, WorkItem> {

    /**
     * Starts a worker of the group through the node, waiting until it opened.
     */
    public TestWorker(QueueTestNode node, String queue, String groupName, WorkerOptions options) throws Exception {
        super(node.vertx(), () -> node.queueService().work(queue, groupName, options));
    }

    public static int value(WorkItem item) {
        return QueueTestSupport.value(item.record());
    }

    /**
     * @return the values leased until none arrives for a while
     */
    public Set<Integer> takeUntilQuiet() throws InterruptedException {
        Set<Integer> ret = new HashSet<>();
        WorkItem item = poll(30_000);
        while (item != null) {
            ret.add(value(item));
            item = poll(1_500);
        }
        return ret;
    }

    /**
     * @return the items leased until {@code count} different values arrived, by value
     */
    public Map<Integer, WorkItem> takeAll(int count) throws InterruptedException {
        Map<Integer, WorkItem> ret = new HashMap<>();
        while (ret.size() < count) {
            WorkItem item = next();
            ret.put(value(item), item);
        }
        return ret;
    }

    public Future<Void> settle(BiFunction<QueueWorker, WorkItem, Future<Void>> settlement, WorkItem item) throws Exception {
        return call(worker -> settlement.apply(worker, item));
    }

    /**
     * @return whether the accept succeeded; it fails when the item's lease ended with its shard's owner, and the item
     * is then leased again
     */
    public boolean tryAccept(WorkItem item) throws Exception {
        return settle(QueueWorker::accept, item).map(true).otherwise(false).await(30, TimeUnit.SECONDS);
    }

    public Future<Void> close() throws Exception {
        return call(QueueWorker::close);
    }
}
