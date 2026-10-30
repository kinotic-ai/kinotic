package org.kinotic.queue.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.ReplyException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.model.WorkItem;
import org.kinotic.queue.api.model.WorkerOptions;
import org.kinotic.queue.internal.QueueTestCluster;
import org.kinotic.queue.internal.QueueTestNode;
import org.kinotic.queue.internal.TestSubscriber;
import org.kinotic.queue.internal.TestWorker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kinotic.queue.internal.QueueTestSupport.await;
import static org.kinotic.queue.internal.QueueTestSupport.keyOf;
import static org.kinotic.queue.internal.QueueTestSupport.payload;
import static org.kinotic.queue.internal.QueueTestSupport.range;
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

    private QueueTestCluster cluster;

    @BeforeEach
    public void setUp() {
        cluster = new QueueTestCluster(directory, REPLICATION_FACTOR);
    }

    @AfterEach
    public void tearDown() throws Exception {
        cluster.close();
    }

    @Test
    public void consumerResumesOnAnotherNodeAfterANodeLeaves() throws Exception {
        QueueTestNode a = cluster.start("a");
        QueueTestNode b = cluster.start("b");
        QueueTestNode c = cluster.start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, SHARDS)));
        appendThrough(List.of(a, b, c), 0, 100);

        TestSubscriber first = new TestSubscriber(b, QUEUE, "billing", StartPosition.EARLIEST);
        for (int i = 0; i < 100; i++) {
            QueueRecord record = first.next();
            if (value(record) < 50) {
                await(first.commit(record));
            }
        }
        await(first.close());

        cluster.stop(a);
        appendThrough(List.of(b, c), 100, 150);

        // Every shard committed exactly its records below 50, so delivery resumes at 50 on each
        assertRedelivers(c, range(50, 150));
    }

    @Test
    public void appendsMadeWithoutWaitingOnANodeNewToTheQueueAreWrittenInTheOrderTheyWereCalled() throws Exception {
        QueueTestNode a = cluster.start("a");
        QueueTestNode b = cluster.start("b");
        cluster.start("c");
        List<String> queues = IntStream.range(0, 6).mapToObj(i -> QUEUE + "-" + i).toList();
        for (String queue : queues) {
            await(a.queueService().createQueueIfNotExist(new QueueDefinition(queue, 1)));
        }

        // Called on one context of a node that has not looked the queues up yet
        Promise<List<Future<QueuePosition>>> called = Promise.promise();
        b.vertx().runOnContext(v -> {
            List<Future<QueuePosition>> appends = new ArrayList<>();
            for (String queue : queues) {
                for (int i = 0; i < 200; i++) {
                    appends.add(b.queueService().append(queue, "same-key", payload(i)));
                }
            }
            called.complete(appends);
        });
        await(Future.all(await(called.future())));

        for (String queue : queues) {
            TestSubscriber subscriber = new TestSubscriber(a, queue, "billing", StartPosition.EARLIEST);
            for (int i = 0; i < 200; i++) {
                assertEquals(i, value(subscriber.next()), "out of order in " + queue);
            }
            await(subscriber.close());
        }
    }

    @Test
    public void anOwnerCutOffFromItsCopiesStepsDownAndServesAgainOnceReachable() throws Exception {
        QueueTestNode a = cluster.start("a");
        QueueTestNode b = cluster.start("b");
        QueueTestNode c = cluster.start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        appendThrough(List.of(a), 0, 10);
        QueueTestNode owner = nodeWithId(a.placement().replicas(QUEUE, 0).getFirst());
        TestSubscriber subscriber = new TestSubscriber(owner, QUEUE, "billing", StartPosition.EARLIEST);
        for (int i = 0; i < 10; i++) {
            subscriber.next();
        }

        // The owner still reaches itself, so only stepping down stops it serving its shard's committed records
        owner.networkFaults().cutOffFrom(Stream.of(a, b, c).filter(node -> node != owner).map(node -> node.placement().localNodeId())
                                               .collect(Collectors.toSet()));
        Throwable failure = subscriber.pollFailure(90_000);
        assertNotNull(failure, "the cut-off owner kept serving its shard");

        owner.networkFaults().heal();
        await(owner.queueService().append(QUEUE, "customer-0", payload(10)));
        assertEquals(10, value(subscriber.next()));
        await(subscriber.close());
    }

    @Test
    public void aRestartedNodeCatchesUpBeforeItServes() throws Exception {
        QueueTestNode a = cluster.start("a");
        QueueTestNode b = cluster.start("b");
        QueueTestNode c = cluster.start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, SHARDS)));
        appendThrough(List.of(a, b, c), 0, 50);

        cluster.stop(c);
        appendThrough(List.of(a, b), 50, 100);
        QueueTestNode restarted = cluster.start("c");
        // Wait until the restarted node holds its copies, which an append through it confirms for one shard
        appendThrough(List.of(restarted), 100, 101);
        cluster.stop(a);

        // Every shard now has its copies on b and the restarted node only, so anything it missed would be lost
        assertRedelivers(b, range(0, 101));
    }

    @Test
    public void recordsAndCommittedPositionsSurviveReplacingEveryNode() throws Exception {
        QueueTestNode a = cluster.start("a");
        QueueTestNode b = cluster.start("b");
        QueueTestNode c = cluster.start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, SHARDS)));
        appendThrough(List.of(a, b, c), 0, 100);
        TestSubscriber first = new TestSubscriber(a, QUEUE, "billing", StartPosition.EARLIEST);
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
            running.add(cluster.start(name));
            cluster.stop(running.removeFirst());
            // A record committed on every shard after each replacement has a majority that includes a node that joined
            // since the previous replacement, so that node holds the shard, and its consumer positions, up to the record
            for (int shard = 0; shard < SHARDS; shard++) {
                await(running.getLast().queueService().append(QUEUE, keyOf(shard, SHARDS), payload(nextValue++)));
            }
        }

        assertRedelivers(running.getLast(), range(50, nextValue));
    }

    @Test
    public void aWorkerGroupKeepsWorkingWhenAShardsOwnerLeaves() throws Exception {
        QueueTestNode a = cluster.start("a");
        QueueTestNode b = cluster.start("b");
        QueueTestNode c = cluster.start("c");
        await(a.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, SHARDS)));
        TestWorker worker = new TestWorker(b, QUEUE, "billing", new WorkerOptions(StartPosition.EARLIEST, 20, Duration.ofSeconds(5)));
        Set<Integer> accepted = new HashSet<>();
        appendThrough(List.of(a, b, c), 0, 40);
        acceptUntil(worker, accepted, 40);

        cluster.stop(a);
        appendThrough(List.of(b, c), 40, 80);

        // Records in flight when their shard's owner left are leased again, so every record is accepted at least once
        acceptUntil(worker, accepted, 80);
        assertEquals(range(0, 80), accepted);
        await(worker.close());
    }

    @Test
    public void appendFailsWithoutAMajorityOfCopies() throws Exception {
        QueueTestNode alone = cluster.start("alone");
        await(alone.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));

        ReplyException e = assertThrows(ReplyException.class, () -> await(alone.queueService().append(QUEUE, "key", payload(0))));

        assertTrue(e.getMessage().contains("copies"), e.getMessage());
    }

    @Test
    public void aSubscriptionReportsAShardItCannotReadForThirtySecondsAndKeepsRetrying() throws Exception {
        QueueTestNode alone = cluster.start("alone");
        await(alone.queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, 1)));
        // With one of three copies, the shard never gets an owner
        TestSubscriber subscriber = new TestSubscriber(alone, QUEUE, "billing", StartPosition.EARLIEST);

        assertNull(subscriber.pollFailure(20_000));
        Throwable failure = subscriber.pollFailure(30_000);

        assertNotNull(failure);
        assertTrue(failure.getMessage().contains("still retrying"), failure.getMessage());
        // Reported once per run of failures
        assertNull(subscriber.pollFailure(2_000));

        QueueTestNode second = cluster.start("second");
        cluster.start("third");
        await(second.queueService().append(QUEUE, "key", payload(7)));
        assertEquals(7, value(subscriber.next()));
        await(subscriber.close());
    }

    @Test
    public void anOwnerRestartedWithAnEmptyCopyLeavesTheShardServedWhileItCatchesUp() throws Exception {
        QueueTestNode a = cluster.start("a");
        cluster.start("b");
        cluster.start("c");
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
        cluster.stop(owner);
        FileUtils.deleteDirectory(directory.resolve(ownerName).toFile());

        QueueTestNode restarted = cluster.start(ownerName);
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

        // The node placed as owner holds none of the shard's records, so the node serving it keeps serving it. The
        // window also spans the restarted node taking the shard over once caught up, which pauses appends briefly, and
        // longer when the machine is busy with other tests.
        assertTrue(slowest < 2_500, "an append took " + slowest + " ms while the restarted copy caught up");
        TestSubscriber subscriber = new TestSubscriber(restarted, QUEUE, "billing", StartPosition.EARLIEST);
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
            cluster.start(name);
        }
        QueueTestNode first = cluster.nodes().getFirst();
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
        cluster.stop(stale);
        QueueTestNode owner = nodeWithId(replicas.getFirst());
        // The node that joined the copies in its place catches up and votes before more records are appended
        QueueTestNode joined = cluster.nodes().stream().filter(node -> !replicas.contains(node.placement().localNodeId())).findFirst().orElseThrow();
        cluster.awaitVoting(QUEUE, 0, joined);
        for (int i = 5; i < 10; i++) {
            await(owner.queueService().append(QUEUE, "key", payload(i)));
        }
        // Gone before the owner records the new committed offset, so only the stored voting copies tell a later owner
        // that the stale copy lacks records
        List<QueueTestNode> voting = new ArrayList<>(cluster.nodes());
        voting.remove(owner);
        voting.addFirst(owner);
        for (QueueTestNode node : voting) {
            cluster.crash(node);
        }

        cluster.start(staleName);
        cluster.start("e");
        QueueTestNode f = cluster.start("f");
        assertThrows(ReplyException.class, () -> await(f.queueService().append(QUEUE, "key", payload(99))));

        for (QueueTestNode node : voting) {
            cluster.start(node.name());
        }
        TestSubscriber subscriber = new TestSubscriber(f, QUEUE, "billing", StartPosition.EARLIEST);
        for (int i = 0; i < 10; i++) {
            assertEquals(i, value(subscriber.next()));
        }
        await(subscriber.close());
    }

    private QueueTestNode nodeWithId(String nodeId) {
        return cluster.nodes().stream().filter(node -> node.placement().localNodeId().equals(nodeId)).findFirst().orElseThrow();
    }

    private static void appendThrough(List<QueueTestNode> through, int from, int to) throws Exception {
        for (int i = from; i < to; i++) {
            await(through.get(i % through.size()).queueService().append(QUEUE, "customer-" + (i % 10), payload(i)));
        }
    }

    // A retried append may be written twice, so delivery is checked for every expected value, in order per key,
    // with repeats allowed
    private static void assertRedelivers(QueueTestNode node, Set<Integer> expected) throws Exception {
        TestSubscriber subscriber = new TestSubscriber(node, QUEUE, "billing", StartPosition.EARLIEST);
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
            if (worker.tryAccept(item)) {
                accepted.add(TestWorker.value(item));
            }
        }
    }
}
