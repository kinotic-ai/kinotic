package org.kinotic.queue.internal.api.services;

import io.vertx.core.eventbus.ReplyException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.internal.QueueTestNode;
import org.kinotic.queue.internal.TestSubscriber;
import org.kinotic.queue.internal.log.QueueLog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kinotic.queue.internal.QueueTestSupport.await;
import static org.kinotic.queue.internal.QueueTestSupport.payload;
import static org.kinotic.queue.internal.QueueTestSupport.value;

/**
 * Behavioral tests for a cluster of queue nodes in the test JVM, each with its own Ignite node, event bus and disk,
 * replicating every shard to three copies.
 */
public class QueueClusterTests {

    private static final int REPLICATION_FACTOR = 3;
    private static final String QUEUE = "orders";
    private static final int SHARDS = 4;

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
    public void consumerResumesOnAnotherNodeAfterANodeLeaves() throws Exception {
        QueueTestNode a = start("a");
        QueueTestNode b = start("b");
        QueueTestNode c = start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, SHARDS)));
        appendThrough(List.of(a, b, c), 0, 100);

        TestSubscriber first = TestSubscriber.subscribe(b.vertx(), b.queueService(), QUEUE, "billing", StartPosition.EARLIEST);
        for (int i = 0; i < 100; i++) {
            QueueRecord record = first.next();
            if (value(record) < 50) {
                await(first.commit(record));
            }
        }
        await(first.close());

        stop(a);
        appendThrough(List.of(b, c), 100, 150);

        // Every shard committed exactly its records below 50, so delivery resumes at 50 on each
        assertRedelivers(c, IntStream.range(50, 150).boxed().collect(Collectors.toSet()));
    }

    @Test
    public void aRestartedNodeCatchesUpBeforeItServes() throws Exception {
        QueueTestNode a = start("a");
        QueueTestNode b = start("b");
        QueueTestNode c = start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, SHARDS)));
        appendThrough(List.of(a, b, c), 0, 50);

        stop(c);
        appendThrough(List.of(a, b), 50, 100);
        QueueTestNode restarted = start("c");
        // Wait until the restarted node holds its copies, which an append through it confirms for one shard
        appendThrough(List.of(restarted), 100, 101);
        stop(a);

        // Every shard now has its copies on b and the restarted node only, so anything it missed would be lost
        assertRedelivers(b, IntStream.range(0, 101).boxed().collect(Collectors.toSet()));
    }

    @Test
    public void recordsAndCommittedPositionsSurviveReplacingEveryNode() throws Exception {
        QueueTestNode a = start("a");
        QueueTestNode b = start("b");
        QueueTestNode c = start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, SHARDS)));
        appendThrough(List.of(a, b, c), 0, 100);
        TestSubscriber first = TestSubscriber.subscribe(a.vertx(), a.queueService(), QUEUE, "billing", StartPosition.EARLIEST);
        for (int i = 0; i < 100; i++) {
            QueueRecord record = first.next();
            if (value(record) < 50) {
                await(first.commit(record));
            }
        }
        await(first.close());

        List<QueueTestNode> running = new ArrayList<>(List.of(a, b, c));
        int nextValue = 100;
        for (String name : List.of("d", "e", "f")) {
            running.add(start(name));
            stop(running.removeFirst());
            // A record committed on every shard after each replacement has a majority that includes a node that joined
            // since the previous replacement, so that node holds the shard, and its consumer positions, up to the record
            for (int shard = 0; shard < SHARDS; shard++) {
                await(running.getLast().queueService().append(QUEUE, keyOf(shard), payload(nextValue++)));
            }
        }

        assertRedelivers(running.getLast(), IntStream.range(50, nextValue).boxed().collect(Collectors.toSet()));
    }

    @Test
    public void appendFailsWithoutAMajorityOfCopies() throws Exception {
        QueueTestNode alone = start("alone");
        await(alone.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));

        ExecutionException e = assertThrows(() -> await(alone.queueService().append(QUEUE, "key", payload(0))));

        assertInstanceOf(ReplyException.class, e.getCause());
        assertTrue(e.getCause().getMessage().contains("copies"), e.getCause().getMessage());
    }

    private QueueTestNode start(String name) throws Exception {
        QueueTestNode ret = QueueTestNode.start(name, directory.resolve(name), REPLICATION_FACTOR);
        nodes.add(ret);
        return ret;
    }

    private void stop(QueueTestNode node) throws Exception {
        nodes.remove(node);
        node.close();
    }

    private static void appendThrough(List<QueueTestNode> through, int from, int to) throws Exception {
        for (int i = from; i < to; i++) {
            await(through.get(i % through.size()).queueService().append(QUEUE, "customer-" + (i % 10), payload(i)));
        }
    }

    // A retried append may be written twice, so delivery is checked for every expected value, in order per key,
    // with repeats allowed
    private static void assertRedelivers(QueueTestNode node, Set<Integer> expected) throws Exception {
        TestSubscriber subscriber = TestSubscriber.subscribe(node.vertx(), node.queueService(), QUEUE, "billing", StartPosition.EARLIEST);
        Set<Integer> received = new HashSet<>();
        Map<String, Integer> lastByKey = new HashMap<>();
        while (!received.containsAll(expected)) {
            QueueRecord record = subscriber.next();
            int value = value(record);
            assertTrue(expected.contains(value), "delivered " + value + ", which was committed");
            Integer last = lastByKey.put(record.key(), value);
            assertTrue(last == null || last <= value, "delivered " + value + " after " + last + " for " + record.key());
            received.add(value);
        }
        await(subscriber.close());
        assertEquals(expected, received);
    }

    private static String keyOf(int shard) {
        int i = 0;
        while (QueueLog.shardOf("shard-key-" + i, SHARDS) != shard) {
            i++;
        }
        return "shard-key-" + i;
    }

    private static ExecutionException assertThrows(org.junit.jupiter.api.function.Executable executable) {
        return org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class, executable);
    }
}
