package org.kinotic.queue.internal.api.services;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.model.WorkItem;
import org.kinotic.queue.api.model.WorkerOptions;
import org.kinotic.queue.api.services.QueueWorker;
import org.kinotic.queue.internal.QueueTestCluster;
import org.kinotic.queue.internal.QueueTestNode;
import org.kinotic.queue.internal.TestSubscriber;
import org.kinotic.queue.internal.TestWorker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kinotic.queue.internal.QueueTestSupport.appendAll;
import static org.kinotic.queue.internal.QueueTestSupport.await;
import static org.kinotic.queue.internal.QueueTestSupport.payload;
import static org.kinotic.queue.internal.QueueTestSupport.value;

/**
 * Behavioral tests for the retention of a three-node cluster whose records are kept for two seconds: old segments are
 * deleted on every copy, readers continue at the oldest record kept, and a copy that lost its disk catches up from the
 * owner's start.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
public class RetentionTests {

    private static final String QUEUE = "orders";
    private static final Map<String, Object> TWO_SECONDS = Map.of("kinotic.queue.retentionPeriod", "2s");

    @TempDir
    private Path directory;

    private QueueTestCluster cluster;

    @BeforeEach
    public void setUp() {
        cluster = new QueueTestCluster(directory, 3, TWO_SECONDS);
    }

    @AfterEach
    public void tearDown() throws Exception {
        cluster.close();
    }

    @Test
    public void oldRecordsAreDeletedOnEveryCopyAndReadersContinueAtTheOldestKept() throws Exception {
        QueueTestNode a = cluster.start("a");
        cluster.start("b");
        cluster.start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        appendAndAge(a);

        // Every copy deletes its first segment, which holds the records the first second appended
        awaitFirstSegmentDeletedOnEveryNode();
        assertDeliversInOrderFrom20Through(a, "billing", 44);

        TestWorker worker = new TestWorker(a, QUEUE, "thumbnails", new WorkerOptions(StartPosition.EARLIEST, 100, Duration.ofMinutes(1)));
        Set<Integer> leased = new HashSet<>();
        // The segment taking records is never deleted, so its records are always leased
        while (!leased.containsAll(Set.of(40, 41, 42, 43, 44))) {
            WorkItem item = worker.next();
            assertTrue(value(item.record()) >= 20, "leased record " + value(item.record()) + ", which was deleted");
            leased.add(value(item.record()));
            await(worker.settle(QueueWorker::accept, item));
        }
        await(worker.close());
    }

    @Test
    public void aCopyThatLostItsDiskCatchesUpFromTheOwnersStart() throws Exception {
        QueueTestNode a = cluster.start("a");
        QueueTestNode b = cluster.start("b");
        QueueTestNode c = cluster.start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        appendAndAge(a);
        awaitFirstSegmentDeletedOnEveryNode();

        cluster.stop(c);
        QueueTestNode emptyC = cluster.start("c", "c-empty");
        await(emptyC.queueService().append(QUEUE, "key", payload(45)));
        // The new copy of c replaces the old one among the copies counting toward commits once it caught up
        cluster.awaitVoting(QUEUE, 0, emptyC);
        cluster.stop(a);

        // With a gone, b and the new copy of c are the majority, which they only form when the new copy holds every
        // record kept
        await(b.queueService().append(QUEUE, "key", payload(46)));
        assertDeliversInOrderFrom20Through(b, "auditor", 46);
    }

    // Appends records 0-19, then 20-39 a second later, then 40-44 two seconds after that; the records of the first
    // second are then older than the retention period
    private static void appendAndAge(QueueTestNode node) throws Exception {
        appendAll(node.queueService(), QUEUE, 0, 20);
        Thread.sleep(1_200);
        appendAll(node.queueService(), QUEUE, 20, 40);
        Thread.sleep(2_200);
        appendAll(node.queueService(), QUEUE, 40, 45);
    }

    private void awaitFirstSegmentDeletedOnEveryNode() {
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            for (QueueTestNode node : cluster.nodes()) {
                assertTrue(firstSegmentDeleted(node), "node " + node.name() + " still holds the shard's first segment");
            }
        });
    }

    // Segment directories are named after the offset they start at
    private static boolean firstSegmentDeleted(QueueTestNode node) {
        Path shard = node.dataDirectory().resolve(QUEUE).resolve("shards").resolve("0");
        boolean ret;
        try (Stream<Path> segments = Files.list(shard)) {
            ret = segments.filter(Files::isDirectory).noneMatch(segment -> segment.getFileName().toString().startsWith("0-"));
        } catch (Exception e) {
            ret = false;
        }
        return ret;
    }

    // Records with one key are delivered in order: none of the deleted first second's, then the rest up to the last
    // one, which the segment taking records holds; the retention may delete more of the older ones meanwhile
    private static void assertDeliversInOrderFrom20Through(QueueTestNode node, String consumerName, int last) throws Exception {
        TestSubscriber subscriber = new TestSubscriber(node, QUEUE, consumerName, StartPosition.EARLIEST);
        int previous = 19;
        while (previous < last) {
            int value = value(subscriber.next());
            assertTrue(value > previous, "delivered " + value + " after " + previous);
            previous = value;
        }
        assertEquals(last, previous);
        await(subscriber.close());
    }
}
