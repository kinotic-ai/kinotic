package org.kinotic.queue.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.eventbus.ReplyException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.model.WorkItem;
import org.kinotic.queue.api.model.WorkerOptions;
import org.kinotic.queue.api.services.QueueWorker;
import org.kinotic.queue.internal.QueueTestNode;
import org.kinotic.queue.internal.QueueTestSupport;
import org.kinotic.queue.internal.TestSubscriber;
import org.kinotic.queue.internal.TestWorker;
import org.kinotic.queue.internal.log.QueueLog;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    public void appendsMadeWithoutWaitingOnANodeNewToTheQueueAreWrittenInTheOrderTheyWereCalled() throws Exception {
        QueueTestNode a = start("a");
        QueueTestNode b = start("b");
        start("c");
        List<String> queues = IntStream.range(0, 6).mapToObj(i -> QUEUE + "-" + i).toList();
        for (String queue : queues) {
            await(a.queueService().createQueueIfNotExist(new QueueDefinition(queue, 1)));
        }

        // Called on one context of a node that has not looked the queues up yet
        CompletableFuture<List<Future<QueuePosition>>> called = new CompletableFuture<>();
        b.vertx().runOnContext(v -> {
            List<Future<QueuePosition>> appends = new ArrayList<>();
            for (String queue : queues) {
                for (int i = 0; i < 200; i++) {
                    appends.add(b.queueService().append(queue, "same-key", payload(i)));
                }
            }
            called.complete(appends);
        });
        for (Future<QueuePosition> append : called.get(30, TimeUnit.SECONDS)) {
            await(append);
        }

        for (String queue : queues) {
            TestSubscriber subscriber = TestSubscriber.subscribe(a.vertx(), a.queueService(), queue, "billing", StartPosition.EARLIEST);
            for (int i = 0; i < 200; i++) {
                assertEquals(i, value(subscriber.next()), "out of order in " + queue);
            }
            await(subscriber.close());
        }
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
    public void aWorkerGroupKeepsWorkingWhenAShardsOwnerLeaves() throws Exception {
        QueueTestNode a = start("a");
        QueueTestNode b = start("b");
        QueueTestNode c = start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, SHARDS)));
        TestWorker worker = TestWorker.start(b.vertx(), b.queueService(), QUEUE, "billing",
                                             new WorkerOptions(StartPosition.EARLIEST, 20, Duration.ofSeconds(5)));
        Set<Integer> accepted = new HashSet<>();
        appendThrough(List.of(a, b, c), 0, 40);
        acceptUntil(worker, accepted, 40);

        stop(a);
        appendThrough(List.of(b, c), 40, 80);

        // Records in flight when their shard's owner left are leased again, so every record is accepted at least once
        acceptUntil(worker, accepted, 80);
        assertEquals(IntStream.range(0, 80).boxed().collect(Collectors.toSet()), accepted);
        await(worker.close());
    }

    @Test
    public void appendFailsWithoutAMajorityOfCopies() throws Exception {
        QueueTestNode alone = start("alone");
        await(alone.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));

        ExecutionException e = assertThrows(() -> await(alone.queueService().append(QUEUE, "key", payload(0))));

        assertInstanceOf(ReplyException.class, e.getCause());
        assertTrue(e.getCause().getMessage().contains("copies"), e.getCause().getMessage());
    }

    @Test
    public void aSubscriptionReportsAShardItCannotReadForThirtySecondsAndKeepsRetrying() throws Exception {
        QueueTestNode alone = start("alone");
        await(alone.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        // With one of three copies, the shard never gets an owner
        TestSubscriber subscriber = TestSubscriber.subscribe(alone.vertx(), alone.queueService(), QUEUE, "billing", StartPosition.EARLIEST);

        assertNull(subscriber.pollFailure(20_000));
        Throwable failure = subscriber.pollFailure(30_000);

        assertNotNull(failure);
        assertTrue(failure.getMessage().contains("still retrying"), failure.getMessage());
        // Reported once per run of failures
        assertNull(subscriber.pollFailure(2_000));

        QueueTestNode second = start("second");
        start("third");
        await(second.queueService().append(QUEUE, "key", payload(7)));
        assertEquals(7, value(subscriber.next()));
        await(subscriber.close());
    }

    @Test
    public void anOwnerRestartedWithAnEmptyCopyLeavesTheShardServedWhileItCatchesUp() throws Exception {
        QueueTestNode a = start("a");
        start("b");
        start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        int fillers = 100_000;
        byte[] filler = new byte[1024];
        for (int from = 0; from < fillers; from += 5_000) {
            List<Future<QueuePosition>> appends = new ArrayList<>();
            for (int i = from; i < from + 5_000; i++) {
                appends.add(a.queueService().append(QUEUE, "filler", filler));
            }
            await(Future.all(appends));
        }
        QueueTestNode owner = nodeWithId(a.placement().replicas(QUEUE, 0).getFirst());
        String ownerName = owner.name();
        stop(owner);
        FileUtils.deleteDirectory(directory.resolve(ownerName).toFile());

        QueueTestNode restarted = start(ownerName);
        assertEquals(restarted.placement().localNodeId(), restarted.placement().replicas(QUEUE, 0).getFirst(),
                     "the restarted node is placed as the shard's owner again");
        // Past the change of topology the restart caused, while the restarted copy is still far from caught up
        Thread.sleep(2_000);
        long slowest = 0;
        int appended = 0;
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until) {
            long started = System.currentTimeMillis();
            await(restarted.queueService().append(QUEUE, "key", payload(appended++)));
            slowest = Math.max(slowest, System.currentTimeMillis() - started);
        }

        // The node placed as owner holds none of the shard's records, so the node serving it keeps serving it
        assertTrue(slowest < 1_000, "an append took " + slowest + " ms while the restarted copy caught up");
        TestSubscriber subscriber = TestSubscriber.subscribe(restarted.vertx(), restarted.queueService(), QUEUE, "billing", StartPosition.EARLIEST);
        int delivered = 0;
        List<Integer> values = new ArrayList<>();
        while (values.size() < appended) {
            QueueRecord record = subscriber.next();
            delivered++;
            if (record.key().equals("key")) {
                values.add(value(record));
            }
        }
        assertEquals(fillers + appended, delivered);
        assertEquals(IntStream.range(0, appended).boxed().toList(), values);
        await(subscriber.close());
    }

    @Test
    public void aStaleCopyDoesNotTakeAShardWhoseVotingCopiesAreAllGone() throws Exception {
        for (String name : List.of("a", "b", "c", "d")) {
            start(name);
        }
        QueueTestNode first = nodes.getFirst();
        await(first.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        for (int i = 0; i < 5; i++) {
            await(first.queueService().append(QUEUE, "key", payload(i)));
        }
        // Long enough for the owner to record the committed offset for later owners
        Thread.sleep(1_500);
        List<String> replicas = first.placement().replicas(QUEUE, 0);
        // A follower that leaves cleanly keeps a copy current up to here
        QueueTestNode stale = nodeWithId(replicas.get(1));
        String staleName = stale.name();
        stop(stale);
        QueueTestNode owner = nodeWithId(replicas.getFirst());
        // The node that joined the copies in its place catches up and votes before more records are appended
        Thread.sleep(3_000);
        for (int i = 5; i < 10; i++) {
            await(owner.queueService().append(QUEUE, "key", payload(i)));
        }
        // Gone before the owner records the new committed offset, so only the stored voting copies tell a later owner
        // that the stale copy lacks records
        List<QueueTestNode> voting = new ArrayList<>(nodes);
        voting.remove(owner);
        voting.addFirst(owner);
        for (QueueTestNode node : voting) {
            nodes.remove(node);
            node.crash();
        }

        start(staleName);
        start("e");
        QueueTestNode f = start("f");
        ExecutionException e = assertThrows(() -> await(f.queueService().append(QUEUE, "key", payload(99))));
        assertInstanceOf(ReplyException.class, e.getCause());

        for (QueueTestNode node : voting) {
            start(node.name());
        }
        TestSubscriber subscriber = TestSubscriber.subscribe(f.vertx(), f.queueService(), QUEUE, "billing", StartPosition.EARLIEST);
        for (int i = 0; i < 10; i++) {
            assertEquals(i, value(subscriber.next()));
        }
        await(subscriber.close());
    }

    private QueueTestNode nodeWithId(String nodeId) {
        return nodes.stream().filter(node -> node.placement().localNodeId().equals(nodeId)).findFirst().orElseThrow();
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

    private static void acceptUntil(TestWorker worker, Set<Integer> accepted, int count) throws Exception {
        while (accepted.size() < count) {
            WorkItem item = worker.next();
            // An accept fails when the item's lease ended with its shard's owner; the item is then leased again
            if (worker.settle(QueueWorker::accept, item).toCompletionStage().toCompletableFuture()
                      .handle((v, e) -> e == null).get(30, TimeUnit.SECONDS)) {
                accepted.add(TestWorker.value(item));
            }
        }
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
