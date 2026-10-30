package org.kinotic.queue.internal;

import io.vertx.core.Future;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.services.QueueSubscription;

/**
 * Drives a {@link QueueSubscription} from the test thread and collects the records it delivers.
 */
public final class TestSubscriber extends TestStream<QueueSubscription, QueueRecord> {

    /**
     * Subscribes through the node, waiting until the subscription opened.
     */
    public TestSubscriber(QueueTestNode node, String queue, String consumerName, StartPosition startPosition) throws Exception {
        super(node.vertx(), () -> node.queueService().subscribe(queue, consumerName, startPosition));
    }

    public Future<Void> commit(QueueRecord record) throws Exception {
        return call(subscription -> subscription.commit(record));
    }

    public Future<Void> close() throws Exception {
        return call(QueueSubscription::close);
    }
}
