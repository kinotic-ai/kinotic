package org.kinotic.queue.internal.api.services;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        for (int i = 0; i < 10; i++) {
            await(service.append(QUEUE, "key", payload(i)));
        }

        // The consumer commits the fourth record, so six committed records follow its position
        TestSubscriber subscriber = TestSubscriber.subscribe(node.vertx(), service, QUEUE, "billing", StartPosition.EARLIEST);
        QueueRecord fourth = null;
        for (int i = 0; i < 4; i++) {
            fourth = subscriber.next();
        }
        await(subscriber.commit(fourth));
        await(subscriber.close());

        // The group rejects record 0, releases record 1 and accepts it once leased again, and holds record 9
        TestWorker worker = TestWorker.start(node.vertx(), service, QUEUE, "thumbnails",
                                             new WorkerOptions(StartPosition.EARLIEST, 10, Duration.ofMinutes(1)));
        Map<Integer, WorkItem> leased = new HashMap<>();
        while (leased.size() < 10) {
            WorkItem item = worker.next();
            leased.put(value(item.record()), item);
        }
        await(worker.settle(QueueWorker::reject, leased.get(0)));
        await(worker.settle(QueueWorker::release, leased.get(1)));
        WorkItem again = worker.next();
        assertEquals(1, value(again.record()));
        await(worker.settle(QueueWorker::accept, again));
        for (int i = 2; i < 9; i++) {
            await(worker.settle(QueueWorker::accept, leased.get(i)));
        }

        assertEquals(10, counter("kinotic.queue.appends", "queue", QUEUE).count());
        assertTrue(registry.find("kinotic.queue.append.duration").tag("queue", QUEUE).timer().count() >= 1);
        assertEquals(1, counter("kinotic.queue.dead.letters", "queue", QUEUE, "group", "thumbnails").count());
        assertEquals(1, counter("kinotic.queue.redeliveries", "queue", QUEUE, "group", "thumbnails").count());
        assertEquals(1, counter("kinotic.queue.takeovers", "queue", QUEUE).count());
        // Gauges are refreshed every five seconds
        awaitGauge(6, () -> gauge("kinotic.queue.consumer.lag", "queue", QUEUE, "consumer", "billing"));
        awaitGauge(1, () -> gauge("kinotic.queue.group.lag", "queue", QUEUE, "group", "thumbnails"));
        awaitGauge(1, () -> gauge("kinotic.queue.shards.owned", "queue", QUEUE));
        assertTrue(gauge("kinotic.queue.size", "queue", QUEUE).value() >= 10 * payload(0).length);
        await(worker.close());

        await(service.deleteQueue(QUEUE));

        long deadline = System.currentTimeMillis() + 15_000;
        while ((registry.find("kinotic.queue.appends").tag("queue", QUEUE).counter() != null
                || registry.find("kinotic.queue.consumer.lag").tag("queue", QUEUE).gauge() != null)
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertNull(registry.find("kinotic.queue.appends").tag("queue", QUEUE).counter(), "the deleted queue's counters stayed");
        assertNull(registry.find("kinotic.queue.consumer.lag").tag("queue", QUEUE).gauge(), "the deleted queue's gauges stayed");
    }

    private Counter counter(String name, String... tags) {
        Counter ret = registry.find(name).tags(tags).counter();
        assertTrue(ret != null, "no counter " + name);
        return ret;
    }

    private Gauge gauge(String name, String... tags) {
        return registry.find(name).tags(tags).gauge();
    }

    private static void awaitGauge(double expected, Supplier<Gauge> gauge) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while ((gauge.get() == null || gauge.get().value() != expected) && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertEquals(expected, gauge.get() != null ? gauge.get().value() : Double.NaN);
    }
}
