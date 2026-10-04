package org.kinotic.queue.internal.api.services;

import io.vertx.core.eventbus.ReplyException;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kinotic.queue.internal.QueueTestSupport.await;
import static org.kinotic.queue.internal.QueueTestSupport.payload;
import static org.kinotic.queue.internal.TestWorker.value;

/**
 * Behavioral tests for worker groups on a single queue node: records are shared between a group's workers, leases
 * expire and are released, records are dropped after their last delivery, and a group's progress survives a restart.
 */
public class QueueWorkerTests {

    private static final String QUEUE = "jobs";
    private static final Duration LONG_LEASE = Duration.ofMinutes(1);

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
    public void eachRecordGoesToOneWorkerOfTheGroup() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition(QUEUE, 4)));
        appendValues(0, 20);
        TestWorker first = work("thumbnails", StartPosition.EARLIEST, 5, LONG_LEASE);
        // The first worker holds what it was leased without settling it, leaving the rest to the second
        Set<Integer> firstValues = first.takeUntilQuiet();
        TestWorker second = work("thumbnails", StartPosition.EARLIEST, 50, LONG_LEASE);
        Set<Integer> secondValues = new HashSet<>();
        while (firstValues.size() + secondValues.size() < 20) {
            secondValues.add(value(second.next()));
        }

        // Prefetch 5, plus at most one more per shard while every shard is asked at once
        assertTrue(firstValues.size() >= 1 && firstValues.size() <= 5 + 4, "first worker held " + firstValues.size());
        assertTrue(firstValues.stream().noneMatch(secondValues::contains), "a record went to both workers");
        Set<Integer> all = new HashSet<>(firstValues);
        all.addAll(secondValues);
        assertEquals(range(0, 20), all);
        assertNull(second.poll(500));
    }

    @Test
    public void anExpiredLeaseGoesToAnotherWorkerAndTheFirstCannotAcceptIt() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        appendValues(0, 1);
        TestWorker slow = work("billing", StartPosition.EARLIEST, 1, Duration.ofMillis(300));
        WorkItem taken = slow.next();
        assertEquals(1, taken.deliveryCount());

        TestWorker other = work("billing", StartPosition.EARLIEST, 1, LONG_LEASE);
        WorkItem retaken = other.next();

        assertEquals(taken.record().position(), retaken.record().position());
        assertEquals(2, retaken.deliveryCount());
        ExecutionException e = assertThrows(ExecutionException.class, () -> await(slow.settle(QueueWorker::accept, taken)));
        assertInstanceOf(ReplyException.class, e.getCause());
        await(other.settle(QueueWorker::accept, retaken));
    }

    @Test
    public void aReleasedRecordReturnsAtOnceUntilItsLastDeliveryThenIsDropped() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        appendValues(0, 2);
        TestWorker worker = work("billing", StartPosition.EARLIEST, 1, LONG_LEASE);

        WorkItem item = worker.next();
        for (int delivery = 1; delivery <= 5; delivery++) {
            assertEquals(0, value(item));
            assertEquals(delivery, item.deliveryCount());
            await(worker.settle(QueueWorker::release, item));
            item = worker.next();
        }

        // The first record was dropped after its fifth delivery, so the second record comes next
        assertEquals(1, value(item));
        assertEquals(1, item.deliveryCount());
    }

    @Test
    public void acceptedAndRejectedRecordsAreNotLeasedAgainAfterARestart() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        appendValues(0, 10);
        TestWorker worker = work("billing", StartPosition.EARLIEST, 10, LONG_LEASE);
        for (int i = 0; i < 10; i++) {
            WorkItem item = worker.next();
            int value = value(item);
            if (value < 5) {
                await(worker.settle(QueueWorker::accept, item));
            } else if (value == 5) {
                await(worker.settle(QueueWorker::reject, item));
            }
        }
        await(worker.close());

        node.close();
        startNode();
        TestWorker resumed = work("billing", StartPosition.EARLIEST, 10, LONG_LEASE);

        assertEquals(range(6, 10), resumed.takeUntilQuiet());
    }

    @Test
    public void aRenewedLeaseOutlivesItsDurationWithoutAnotherDelivery() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        appendValues(0, 1);
        TestWorker busy = work("billing", StartPosition.EARLIEST, 1, Duration.ofMillis(400));
        WorkItem item = busy.next();
        TestWorker idle = work("billing", StartPosition.EARLIEST, 1, LONG_LEASE);

        // Renewed for three times the lease duration while the other worker waits
        for (int i = 0; i < 6; i++) {
            Thread.sleep(200);
            await(busy.settle(QueueWorker::renew, item));
        }

        assertNull(idle.poll(100));
        await(busy.settle(QueueWorker::accept, item));
        assertNull(idle.poll(1_000));
    }

    @Test
    public void aConsumerAndAGroupWithTheSameNameKeepSeparatePositions() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        appendValues(0, 5);
        TestSubscriber subscriber = TestSubscriber.subscribe(node.vertx(), service, QUEUE, "billing", StartPosition.EARLIEST);
        QueueRecord last = null;
        for (int i = 0; i < 5; i++) {
            last = subscriber.next();
        }
        await(subscriber.commit(last));
        await(subscriber.close());

        TestWorker worker = work("billing", StartPosition.EARLIEST, 10, LONG_LEASE);

        assertEquals(range(0, 5), worker.takeUntilQuiet());
    }

    @Test
    public void aGroupStartingAtTheLatestRecordSkipsEarlierOnes() throws Exception {
        await(service.createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        appendValues(0, 5);
        TestWorker worker = work("audit", StartPosition.LATEST, 10, LONG_LEASE);
        assertNull(worker.poll(1_500));

        appendValues(5, 6);

        assertEquals(5, value(worker.next()));
    }

    private void startNode() throws Exception {
        node = QueueTestNode.start("single", directory.resolve("single"), 1);
        service = node.queueService();
    }

    private void appendValues(int from, int to) throws Exception {
        for (int i = from; i < to; i++) {
            await(service.append(QUEUE, "key-" + i, payload(i)));
        }
    }

    private TestWorker work(String groupName, StartPosition startPosition, int prefetch, Duration leaseDuration) throws Exception {
        return TestWorker.start(node.vertx(), service, QUEUE, groupName, new WorkerOptions(startPosition, prefetch, leaseDuration));
    }

    private static Set<Integer> range(int from, int to) {
        return IntStream.range(from, to).boxed().collect(Collectors.toSet());
    }
}
