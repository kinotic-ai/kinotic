package org.kinotic.queue.internal.api.services;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.kinotic.queue.internal.QueueTestSupport.await;
import static org.kinotic.queue.internal.QueueTestSupport.payload;
import static org.kinotic.queue.internal.QueueTestSupport.value;

/**
 * Tests that consumer and worker group positions last while in use and expire once unused for the retention period,
 * on a single node keeping records for sixteen seconds, which refreshes positions in use every four and drops a worker
 * group idle for eight. Nothing is written after the first ten records, so the segment holding them stays open and
 * none of them is deleted.
 */
public class PositionExpiryTests {

    private static final String QUEUE = "orders";

    @TempDir
    private Path directory;

    private QueueTestNode node;
    private QueueService service;

    private static final long LONGER_THAN_RETENTION_MS = 17_000;
    private static final long LONGER_THAN_GROUP_IDLE_MS = 9_000;

    @BeforeEach
    public void setUp() throws Exception {
        startNode();
        await(service.createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        for (int i = 0; i < 10; i++) {
            await(service.append(QUEUE, "key", payload(i)));
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        node.close();
    }

    @Test
    public void aConsumersPositionLastsWhileSubscribedAndExpiresOnceUnused() throws Exception {
        TestSubscriber subscriber = subscribe();
        QueueRecord fifth = null;
        for (int i = 0; i < 5; i++) {
            fifth = subscriber.next();
        }
        await(subscriber.commit(fifth));
        // Nothing new to commit
        Thread.sleep(LONGER_THAN_RETENTION_MS);
        await(subscriber.close());

        TestSubscriber resumed = subscribe();
        assertEquals(5, value(resumed.next()));
        await(resumed.close());

        Thread.sleep(LONGER_THAN_RETENTION_MS);
        TestSubscriber restarted = subscribe();
        assertEquals(0, value(restarted.next()));
        await(restarted.close());
    }

    @Test
    public void aGroupsPositionLastsWhileItsWorkersAskAndExpiresOnceTheGroupIsGone() throws Exception {
        TestWorker worker = work();
        for (WorkItem item : takeAll(worker, 10).values()) {
            if (value(item.record()) < 5) {
                await(worker.settle(QueueWorker::accept, item));
            }
        }
        // Holding records 5 to 9, so the group makes no progress
        Thread.sleep(LONGER_THAN_RETENTION_MS);
        await(worker.close());
        // The shard's owner drops the idle group, so the group resumes from its stored position
        Thread.sleep(LONGER_THAN_GROUP_IDLE_MS);

        TestWorker resumed = work();
        assertEquals(range(5, 10), takeAll(resumed, 5).keySet());
        await(resumed.close());

        Thread.sleep(LONGER_THAN_RETENTION_MS);
        TestWorker restarted = work();
        assertEquals(range(0, 10), takeAll(restarted, 10).keySet());
        await(restarted.close());
    }

    private void startNode() throws Exception {
        node = QueueTestNode.start("single", directory.resolve("single"), 1, Map.of("kinotic.queue.retentionPeriod", "16s"));
        service = node.queueService();
    }

    private TestSubscriber subscribe() throws Exception {
        return TestSubscriber.subscribe(node.vertx(), service, QUEUE, "billing", StartPosition.EARLIEST);
    }

    private TestWorker work() throws Exception {
        return TestWorker.start(node.vertx(), service, QUEUE, "thumbnails", new WorkerOptions(StartPosition.EARLIEST, 10, Duration.ofMinutes(1)));
    }

    private static Map<Integer, WorkItem> takeAll(TestWorker worker, int count) throws Exception {
        Map<Integer, WorkItem> ret = new HashMap<>();
        while (ret.size() < count) {
            WorkItem item = worker.next();
            ret.put(value(item.record()), item);
        }
        return ret;
    }

    private static Set<Integer> range(int from, int to) {
        return IntStream.range(from, to).boxed().collect(Collectors.toCollection(HashSet::new));
    }
}
