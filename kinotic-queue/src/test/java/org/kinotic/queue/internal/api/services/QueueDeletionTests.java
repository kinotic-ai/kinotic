package org.kinotic.queue.internal.api.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.model.WorkItem;
import org.kinotic.queue.api.model.WorkerOptions;
import org.kinotic.queue.api.services.QueueWorker;
import org.kinotic.queue.internal.QueueTestNode;
import org.kinotic.queue.internal.TestSubscriber;
import org.kinotic.queue.internal.TestWorker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kinotic.queue.internal.QueueTestSupport.await;
import static org.kinotic.queue.internal.QueueTestSupport.payload;
import static org.kinotic.queue.internal.QueueTestSupport.value;

/**
 * Behavioral tests for deleting a queue on a three-node cluster: its readers end, appends fail, every copy goes, a
 * queue of the same name starts empty, and a node away during the deletion, or every node restarting, does not bring
 * the queue back.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
public class QueueDeletionTests {

    private static final String QUEUE = "orders";

    @TempDir
    private Path directory;

    private final List<QueueTestNode> nodes = new ArrayList<>();

    @AfterEach
    public void tearDown() throws Exception {
        for (QueueTestNode node : nodes) {
            node.close();
        }
    }

    @Test
    public void aDeletedQueueEndsItsReadersFailsAppendsLosesEveryCopyAndStartsEmptyWhenCreatedAgain() throws Exception {
        QueueTestNode a = start("a");
        QueueTestNode b = start("b");
        QueueTestNode c = start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 2)));
        append(a, 0, 10);
        TestSubscriber subscriber = TestSubscriber.subscribe(b.vertx(), b.queueService(), QUEUE, "billing", StartPosition.EARLIEST);
        TestWorker worker = TestWorker.start(c.vertx(), c.queueService(), QUEUE, "thumbnails",
                                             new WorkerOptions(StartPosition.EARLIEST, 1, Duration.ofMinutes(1)));
        subscriber.next();
        WorkItem held = worker.next();

        await(a.queueService().deleteQueue(QUEUE));

        Throwable subscriptionEnd = subscriber.pollFailure(30_000);
        assertNotNull(subscriptionEnd, "the subscription of a deleted queue kept running");
        assertTrue(subscriptionEnd.getMessage().contains("deleted"), subscriptionEnd.getMessage());
        // A worker holding all it may learns of the deletion once it settles what it holds and asks for more
        assertThrows(ExecutionException.class, () -> await(worker.settle(QueueWorker::accept, held)));
        assertNotNull(worker.pollFailure(30_000), "the worker of a deleted queue kept running");
        assertThrows(ExecutionException.class, () -> await(b.queueService().append(QUEUE, "key", payload(10))));
        awaitNoCopy(a, b, c);

        // A queue created again with the same name holds none of the deleted queue's records or positions
        assertEquals(3, await(c.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 3))).shardCount());
        append(c, 100, 101);
        TestSubscriber again = TestSubscriber.subscribe(a.vertx(), a.queueService(), QUEUE, "billing", StartPosition.EARLIEST);
        assertEquals(100, value(again.next()));
        await(again.close());
    }

    @Test
    public void aNodeAwayDuringTheDeletionDeletesItsCopyWhenItReturns() throws Exception {
        QueueTestNode a = start("a");
        start("b");
        QueueTestNode c = start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        append(a, 0, 10);
        stop(c);

        await(a.queueService().deleteQueue(QUEUE));
        QueueTestNode returned = start("c");

        awaitNoCopy(returned);
        // The returned copy did not make the deleted queue known again
        assertEquals(2, await(returned.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 2))).shardCount());
    }

    @Test
    public void aDeletedQueueStaysDeletedWhenEveryNodeRestartsAndTheNodeThatMissedTheDeletionStartsFirst() throws Exception {
        QueueTestNode a = start("a");
        QueueTestNode b = start("b");
        QueueTestNode c = start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        append(a, 0, 10);
        stop(c);
        await(a.queueService().deleteQueue(QUEUE));
        awaitNoCopy(a, b);
        stop(a);
        stop(b);

        // Alone, the node holding the only copy makes the queue known again, until the others say it was deleted
        QueueTestNode first = start("c");
        start("a");
        start("b");

        awaitNoCopy(first);
        assertThrows(ExecutionException.class, () -> await(first.queueService().append(QUEUE, "key", payload(10))));
    }

    private static void append(QueueTestNode node, int from, int to) throws Exception {
        for (int i = from; i < to; i++) {
            await(node.queueService().append(QUEUE, "key", payload(i)));
        }
    }

    private static void awaitNoCopy(QueueTestNode... holders) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        for (QueueTestNode node : holders) {
            Path copy = node.dataDirectory().resolve(QUEUE);
            while (Files.exists(copy) && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            assertFalse(Files.exists(copy), "node " + node.name() + " still holds a copy of the deleted queue");
        }
    }

    private QueueTestNode start(String name) throws Exception {
        QueueTestNode ret = QueueTestNode.start(name, directory.resolve(name), 3);
        nodes.add(ret);
        return ret;
    }

    private void stop(QueueTestNode node) throws Exception {
        nodes.remove(node);
        node.close();
    }
}
