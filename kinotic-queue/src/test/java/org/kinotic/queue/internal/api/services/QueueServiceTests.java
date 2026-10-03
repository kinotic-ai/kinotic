package org.kinotic.queue.internal.api.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.services.QueueService;
import org.kinotic.queue.api.services.QueueSubscription;
import org.kinotic.queue.internal.QueueTestNode;
import org.kinotic.queue.internal.TestSubscriber;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kinotic.queue.internal.QueueTestSupport.await;
import static org.kinotic.queue.internal.QueueTestSupport.payload;
import static org.kinotic.queue.internal.QueueTestSupport.value;

/**
 * Behavioral tests for {@link DefaultQueueService} on a single queue node holding real Chronicle Queue files:
 * per-key ordering, at-least-once resume after the node restarts, start positions, and backpressure.
 */
public class QueueServiceTests {

    @TempDir
    private Path directory;

    private QueueTestNode node;
    private QueueService service;

    @BeforeEach
    public void setUp() throws Exception {
        startNode();
    }

    @AfterEach
    public void tearDown() throws Exception {
        node.close();
    }

    @Test
    public void createQueueIfNotExistKeepsTheOriginalShardCount() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition("orders", 4)));

        QueueDefinition stored = await(service.createQueueIfNotExist(new QueueDefinition("orders", 8)));

        assertEquals(4, stored.shardCount());
    }

    @Test
    public void recordsWithTheSameKeyAreDeliveredInAppendOrder() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition("orders", 4)));
        for (int i = 0; i < 50; i++) {
            await(service.append("orders", "customer-" + (i % 5), payload(i)));
        }

        TestSubscriber subscriber = subscribe("billing", StartPosition.EARLIEST);
        Map<String, List<Integer>> byKey = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            QueueRecord record = subscriber.next();
            byKey.computeIfAbsent(record.key(), k -> new ArrayList<>()).add(value(record));
        }

        assertEquals(5, byKey.size());
        byKey.forEach((key, values) -> {
            int customer = Integer.parseInt(key.substring("customer-".length()));
            for (int i = 0; i < values.size(); i++) {
                assertEquals(customer + i * 5, values.get(i), "out of order for " + key);
            }
        });
    }

    @Test
    public void consumerResumesAfterItsLastCommitAcrossARestart() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition("orders", 1)));
        long lastOffset = -1;
        for (int i = 0; i < 10; i++) {
            QueuePosition position = await(service.append("orders", "key", payload(i)));
            assertTrue(position.offset() > lastOffset);
            lastOffset = position.offset();
        }
        TestSubscriber first = subscribe("billing", StartPosition.EARLIEST);
        QueueRecord committed = null;
        for (int i = 0; i < 6; i++) {
            QueueRecord record = first.next();
            if (i == 3) {
                committed = record;
            }
        }
        await(first.commit(committed));
        await(first.close());

        // The restarted node is a new cluster member that knows the queue only from its disk
        node.close();
        startNode();
        assertTrue(await(service.append("orders", "key", payload(10))).offset() > lastOffset);

        TestSubscriber resumed = subscribe("billing", StartPosition.EARLIEST);
        for (int i = 4; i <= 10; i++) {
            assertEquals(i, value(resumed.next()));
        }
        assertNull(resumed.poll(500));
    }

    @Test
    public void latestStartsWithTheNextAppendedRecord() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition("orders", 2)));
        for (int i = 0; i < 5; i++) {
            await(service.append("orders", "key-" + i, payload(i)));
        }

        TestSubscriber subscriber = subscribe("audit", StartPosition.LATEST);
        assertNull(subscriber.poll(1_500));
        await(service.append("orders", "key-5", payload(5)));

        assertEquals(5, value(subscriber.next()));
    }

    @Test
    public void pausedSubscriptionDeliversOnlyWhatIsFetched() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition("orders", 1)));
        TestSubscriber subscriber = subscribe("billing", StartPosition.EARLIEST);
        subscriber.onContext(QueueSubscription::pause);
        for (int i = 0; i < 5; i++) {
            await(service.append("orders", "key", payload(i)));
        }
        assertNull(subscriber.poll(500));

        subscriber.onContext(s -> s.fetch(2));

        assertEquals(0, value(subscriber.next()));
        assertEquals(1, value(subscriber.next()));
        assertNull(subscriber.poll(500));
    }

    @Test
    public void commitOfARecordNotYetDeliveredFails() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition("orders", 1)));
        await(service.append("orders", "key", payload(0)));
        TestSubscriber subscriber = subscribe("billing", StartPosition.EARLIEST);
        QueueRecord delivered = subscriber.next();
        QueueRecord ahead = new QueueRecord(new QueuePosition("orders", 0, delivered.position().offset() + 1),
                                            "key", payload(1));

        ExecutionException e = assertThrows(ExecutionException.class, () -> await(subscriber.commit(ahead)));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    @Test
    public void appendOfARecordLargerThanTheMaximumEventPayloadFails() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition("orders", 1)));

        ExecutionException e = assertThrows(ExecutionException.class,
                                             () -> await(service.append("orders", "key", new byte[2 * 1024 * 1024])));

        assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    @Test
    public void appendToAMissingOrInvalidQueueFails() {
        ExecutionException missing = assertThrows(ExecutionException.class,
                                                  () -> await(service.append("missing", "key", payload(0))));
        assertInstanceOf(IllegalArgumentException.class, missing.getCause());

        ExecutionException traversal = assertThrows(ExecutionException.class,
                                                    () -> await(service.append("../outside", "key", payload(0))));
        assertInstanceOf(IllegalArgumentException.class, traversal.getCause());
    }

    private void startNode() throws Exception {
        node = QueueTestNode.start("single", directory.resolve("single"), 1);
        service = node.queueService();
    }

    private TestSubscriber subscribe(String consumerName, StartPosition startPosition) throws Exception {
        return TestSubscriber.subscribe(node.vertx(), service, "orders", consumerName, startPosition);
    }
}
