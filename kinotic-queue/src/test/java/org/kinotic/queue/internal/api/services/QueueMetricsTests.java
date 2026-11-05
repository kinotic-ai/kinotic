package org.kinotic.queue.internal.api.services;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.model.WorkItem;
import org.kinotic.queue.api.model.WorkerOptions;
import org.kinotic.queue.api.services.QueueService;
import org.kinotic.queue.api.services.QueueWorker;
import org.kinotic.queue.internal.QueueTestNode;
import org.kinotic.queue.internal.TestSubscriber;
import org.kinotic.queue.internal.TestWorker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kinotic.queue.internal.QueueTestSupport.appendAll;
import static org.kinotic.queue.internal.QueueTestSupport.await;
import static org.kinotic.queue.internal.QueueTestSupport.payload;
import static org.kinotic.queue.internal.QueueTestSupport.value;

/**
 * Tests the meters a queue node registers with Micrometer's global registry, read through a registry added to it, on
 * a single node that owns every shard.
 */
public class QueueMetricsTests {

    private static final String QUEUE = "orders";

    @TempDir
    private Path directory;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private QueueTestNode node;

    @BeforeEach
    public void setUp() throws Exception {
        Metrics.globalRegistry.add(registry);
        node = QueueTestNode.start("single", directory.resolve("single"), 1);
    }

    @AfterEach
    public void tearDown() throws Exception {
        node.close();
        Metrics.globalRegistry.remove(registry);
        registry.close();
    }

    @Test
    public void appendsLagDeadLettersRedeliveriesAndOwnershipAreMeasuredAndADeletedQueuesMetersGo() throws Exception {
        QueueService service = node.queueService();
        await(service.createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        appendAll(service, QUEUE, 0, 10);

        // The consumer commits the fourth record, so six committed records follow its position
        TestSubscriber subscriber = new TestSubscriber(node, QUEUE, "billing", StartPosition.EARLIEST);
        QueueRecord fourth = null;
        for (int i = 0; i < 4; i++) {
            fourth = subscriber.next();
        }
        await(subscriber.commit(fourth));
        await(subscriber.close());

        // The group rejects record 0, releases record 1 and accepts it once leased again, and holds record 9
        TestWorker worker = new TestWorker(node, QUEUE, "thumbnails", new WorkerOptions(StartPosition.EARLIEST, 10, Duration.ofMinutes(1)));
        Map<Integer, WorkItem> leased = worker.takeAll(10);
        await(worker.settle(QueueWorker::reject, leased.get(0)));
        await(worker.settle(QueueWorker::release, leased.get(1)));
        WorkItem again = worker.next();
        assertEquals(1, value(again.record()));
        await(worker.settle(QueueWorker::accept, again));
        for (int i = 2; i < 9; i++) {
            await(worker.settle(QueueWorker::accept, leased.get(i)));
        }

        assertEquals(10, registry.get("kinotic.queue.appends").tags("queue", QUEUE).counter().count());
        assertTrue(registry.get("kinotic.queue.append.duration").tags("queue", QUEUE).timer().count() >= 1);
        assertEquals(1, registry.get("kinotic.queue.dead.letters").tags("queue", QUEUE, "group", "thumbnails").counter().count());
        assertEquals(1, registry.get("kinotic.queue.redeliveries").tags("queue", QUEUE, "group", "thumbnails").counter().count());
        assertEquals(1, registry.get("kinotic.queue.takeovers").tags("queue", QUEUE).counter().count());
        // Gauges are refreshed every five seconds
        Awaitility.await().atMost(Duration.ofSeconds(15)).ignoreException(MeterNotFoundException.class).untilAsserted(() -> {
            assertEquals(6, registry.get("kinotic.queue.consumer.lag").tags("queue", QUEUE, "consumer", "billing").gauge().value());
            assertEquals(1, registry.get("kinotic.queue.group.lag").tags("queue", QUEUE, "group", "thumbnails").gauge().value());
            assertEquals(1, registry.get("kinotic.queue.shards.owned").tags("queue", QUEUE).gauge().value());
        });
        assertTrue(registry.get("kinotic.queue.size").tags("queue", QUEUE).gauge().value() >= 10 * payload(0).length);
        await(worker.close());

        await(service.deleteQueue(QUEUE));

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertNull(registry.find("kinotic.queue.appends").tag("queue", QUEUE).counter(), "the deleted queue's counters stayed");
            assertNull(registry.find("kinotic.queue.consumer.lag").tag("queue", QUEUE).gauge(), "the deleted queue's gauges stayed");
        });
    }
}
