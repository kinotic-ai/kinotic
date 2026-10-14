package org.kinotic.queue.internal.api.services;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.model.WorkItem;
import org.kinotic.queue.api.model.WorkerOptions;
import org.kinotic.queue.api.services.QueueWorker;
import org.kinotic.queue.internal.QueueTestNode;
import org.kinotic.queue.internal.QueueWorkload;
import org.kinotic.queue.internal.TestSubscriber;
import org.kinotic.queue.internal.TestWorker;
import org.kinotic.queue.internal.log.QueueLog;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kinotic.queue.internal.QueueTestSupport.await;
import static org.kinotic.queue.internal.QueueTestSupport.value;

/**
 * Chaos tests for a cluster of three queue nodes in the test JVM, every shard copied to all three. Each test keeps
 * appending through the live nodes while it stops, crashes, isolates, slows or wipes nodes, then checks the queue's
 * promises held: every acknowledged record is delivered, in order per key, and nothing that was never appended is.
 */
// Each test runs a cluster through several faults, each taking up to the queue client's append deadline to recover from
@Timeout(value = 5, unit = TimeUnit.MINUTES)
public class ChaosTests {

    private static final String QUEUE = "orders";
    private static final int SHARDS = 4;
    // Enough acknowledged appends for every shard to receive some, since the workload spreads 16 keys over the shards
    private static final int PROGRESS = 40;
    private static final long LOSSY_PHASE_MILLIS = 30_000;
    private static final Duration LEASE = Duration.ofSeconds(3);

    @TempDir
    private Path directory;

    private final List<QueueTestNode> nodes = new ArrayList<>();
    private final List<QueueTestNode> live = new CopyOnWriteArrayList<>();
    private QueueWorkload workload;

    @AfterEach
    public void tearDown() throws Exception {
        if (workload != null) {
            workload.close();
        }
        for (QueueTestNode node : nodes) {
            node.close();
        }
    }

    @Test
    public void rollingRestartsUnderLoadLoseNoAcknowledgedRecord() throws Exception {
        startCluster();
        for (String name : List.of("a", "b", "c")) {
            QueueTestNode node = node(name);
            removeFromLoad(node);
            stop(node);
            workload.awaitAcknowledged(PROGRESS);
            addToLoad(start(name));
            workload.awaitAcknowledged(PROGRESS);
        }
        workload.close();

        workload.verifyDelivered(live.getFirst(), "verifier");
    }

    @Test
    public void rollingCrashesUnderLoadLoseNoAcknowledgedRecord() throws Exception {
        startCluster();
        for (String name : List.of("a", "b", "c")) {
            QueueTestNode node = node(name);
            removeFromLoad(node);
            crash(node);
            workload.awaitAcknowledged(PROGRESS);
            addToLoad(start(name));
            workload.awaitAcknowledged(PROGRESS);
        }
        workload.close();

        workload.verifyDelivered(live.getFirst(), "verifier");
    }

    @Test
    public void aNodeCutOffFromTheNetworkHealsWithoutLosingAcknowledgedRecords() throws Exception {
        startCluster();
        QueueTestNode cutOff = node("b");
        removeFromLoad(cutOff);
        // Ignite still counts the node as a member, so it keeps its shards while nobody can reach it
        cutOff.networkFaults().isolate();
        Thread.sleep(4_000);
        cutOff.networkFaults().heal();
        addToLoad(cutOff);
        workload.awaitAcknowledged(PROGRESS);
        workload.close();

        workload.verifyDelivered(cutOff, "verifier");
    }

    @Test
    public void aLossySlowNetworkLosesNoAcknowledgedRecord() throws Exception {
        startCluster();
        nodes.forEach(node -> node.networkFaults().degrade(0.1, 30));
        int acknowledgedBefore = workload.acknowledged().size();
        // A lost request holds the workload's one append until the client's timeout, so the lossy phase lasts a fixed time
        Thread.sleep(LOSSY_PHASE_MILLIS);
        assertTrue(workload.acknowledged().size() > acknowledgedBefore, "no append was acknowledged on the lossy network");
        nodes.forEach(node -> node.networkFaults().heal());
        workload.awaitAcknowledged(PROGRESS);
        workload.close();

        workload.verifyDelivered(live.getFirst(), "verifier");
    }

    @Test
    public void losingAMajorityStopsAcknowledgementsAndRegainingItLosesNothing() throws Exception {
        startCluster();
        QueueTestNode b = node("b");
        QueueTestNode c = node("c");
        removeFromLoad(b);
        removeFromLoad(c);
        stop(b);
        stop(c);
        // An append that was already on its way when the majority went may still have been acknowledged
        Thread.sleep(2_000);
        int acknowledgedWithoutMajority = workload.acknowledged().size();
        Thread.sleep(3_000);
        assertEquals(acknowledgedWithoutMajority, workload.acknowledged().size(), "an append was acknowledged by one copy of three");

        addToLoad(start("b"));
        addToLoad(start("c"));
        workload.awaitAcknowledged(PROGRESS);
        workload.close();

        workload.verifyDelivered(live.getFirst(), "verifier");
    }

    @Test
    public void wipingDisksOneNodeAtATimeLosesNothing() throws Exception {
        startCluster();
        assertEveryShardHasAWorkloadKey();

        QueueTestNode c = node("c");
        removeFromLoad(c);
        stop(c);
        FileUtils.deleteDirectory(c.dataDirectory().toFile());
        addToLoad(start("c"));
        QueueTestNode a = node("a");
        removeFromLoad(a);
        stop(a);
        // With only b and c running, an acknowledged append needs both, so c holds every shard up to it
        workload.awaitAcknowledged(PROGRESS);

        QueueTestNode b = node("b");
        removeFromLoad(b);
        stop(b);
        FileUtils.deleteDirectory(b.dataDirectory().toFile());
        // c alone holds the queue now, so the wiped b and the returning a must bring their copies level with it
        addToLoad(start("b"));
        addToLoad(start("a"));
        workload.awaitAcknowledged(PROGRESS);
        workload.close();

        workload.verifyDelivered(node("b"), "verifier");
    }

    @Test
    public void aFullClusterRestartKeepsRecordsAndPositions() throws Exception {
        startCluster();
        workload.close();
        Set<Integer> acknowledged = workload.acknowledged();
        // A consumer and a worker group each finish every acknowledged record
        TestSubscriber consumer = TestSubscriber.subscribe(node("a").vertx(), node("a").queueService(), QUEUE, "billing", StartPosition.EARLIEST);
        Set<Integer> consumed = new HashSet<>();
        while (!consumed.containsAll(acknowledged)) {
            QueueRecord record = consumer.next();
            await(consumer.commit(record));
            consumed.add(value(record));
        }
        await(consumer.close());
        TestWorker worker = TestWorker.start(node("b").vertx(), node("b").queueService(), QUEUE, "jobs",
                                             new WorkerOptions(StartPosition.EARLIEST, 50, Duration.ofMinutes(1)));
        Set<Integer> worked = new HashSet<>();
        while (!worked.containsAll(acknowledged)) {
            WorkItem item = worker.next();
            await(worker.settle(QueueWorker::accept, item));
            worked.add(TestWorker.value(item));
        }
        await(worker.close());

        for (String name : List.of("a", "b", "c")) {
            QueueTestNode node = node(name);
            live.remove(node);
            stop(node);
        }
        for (String name : List.of("c", "a", "b")) {
            live.add(start(name));
        }

        workload.verifyDelivered(node("c"), "verifier");
        TestSubscriber resumedConsumer = TestSubscriber.subscribe(node("a").vertx(), node("a").queueService(), QUEUE, "billing", StartPosition.EARLIEST);
        assertNull(resumedConsumer.poll(3_000), "the consumer received a record it had committed");
        TestWorker resumedWorker = TestWorker.start(node("b").vertx(), node("b").queueService(), QUEUE, "jobs",
                                                    new WorkerOptions(StartPosition.EARLIEST, 50, Duration.ofMinutes(1)));
        assertNull(resumedWorker.poll(3_000), "the group received a record it had accepted");
        await(resumedConsumer.close());
        await(resumedWorker.close());
    }

    @Test
    public void aWorkerGroupFinishesEveryRecordWhileNodesCrash() throws Exception {
        startCluster();
        QueueTestNode workerNode = node("c");
        TestWorker worker = TestWorker.start(workerNode.vertx(), workerNode.queueService(), QUEUE, "jobs",
                                             new WorkerOptions(StartPosition.EARLIEST, 20, LEASE));
        Set<Integer> received = ConcurrentHashMap.newKeySet();
        AtomicLong lastReceived = new AtomicLong(System.currentTimeMillis());
        Thread working = Thread.ofPlatform().start(() -> acceptWhileRunning(worker, received, lastReceived));

        for (String name : List.of("a", "b")) {
            QueueTestNode node = node(name);
            removeFromLoad(node);
            crash(node);
            workload.awaitAcknowledged(PROGRESS);
            addToLoad(start(name));
            workload.awaitAcknowledged(PROGRESS);
        }
        workload.close();

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(120);
        while (!received.containsAll(workload.acknowledged()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        // A record whose accept did not take effect comes back once its lease expires, so a worker quiet for longer
        // than a lease has finished every record
        while (System.currentTimeMillis() - lastReceived.get() < 2 * LEASE.toMillis()) {
            Thread.sleep(100);
        }
        working.interrupt();
        working.join();
        await(worker.close());
        Set<Integer> missing = new HashSet<>(workload.acknowledged());
        missing.removeAll(received);
        assertTrue(missing.isEmpty(), "acknowledged records no worker received: " + missing);
        TestWorker checker = TestWorker.start(node("a").vertx(), node("a").queueService(), QUEUE, "jobs",
                                              new WorkerOptions(StartPosition.EARLIEST, 20, Duration.ofMinutes(1)));
        assertNull(checker.poll(3_000), "the group still had records to work after its worker finished");
        await(checker.close());
    }

    @Test
    public void aConsumerResumesAfterItsLastCommitWhenItsNodeCrashes() throws Exception {
        startCluster();
        QueueTestNode consumerNode = node("a");
        removeFromLoad(consumerNode);
        TestSubscriber consumer = TestSubscriber.subscribe(consumerNode.vertx(), consumerNode.queueService(), QUEUE, "billing", StartPosition.EARLIEST);
        Set<Integer> committed = new HashSet<>();
        for (int i = 0; i < 2 * PROGRESS; i++) {
            QueueRecord record = consumer.next();
            await(consumer.commit(record));
            committed.add(value(record));
        }

        crash(consumerNode);
        workload.awaitAcknowledged(PROGRESS);
        workload.close();

        // A commit covers the records before it on its shard, and the committed ones are never delivered again
        QueueTestNode resumeNode = node("b");
        TestSubscriber resumed = TestSubscriber.subscribe(resumeNode.vertx(), resumeNode.queueService(), QUEUE, "billing", StartPosition.EARLIEST);
        Set<Integer> expected = new HashSet<>(workload.acknowledged());
        expected.removeAll(committed);
        Set<Integer> delivered = new HashSet<>();
        while (!delivered.containsAll(expected)) {
            int value = value(resumed.next());
            assertFalse(committed.contains(value), "delivered " + value + " again after it was committed");
            delivered.add(value);
        }
        await(resumed.close());
    }

    private void startCluster() throws Exception {
        for (String name : List.of("a", "b", "c")) {
            live.add(start(name));
        }
        await(live.getFirst().queueService().createQueueIfNotExist(new QueueDefinition(QUEUE, SHARDS)));
        workload = new QueueWorkload(QUEUE, live, 0);
        workload.awaitAcknowledged(PROGRESS);
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

    private void crash(QueueTestNode node) throws Exception {
        nodes.remove(node);
        node.crash();
    }

    private QueueTestNode node(String name) {
        return nodes.stream().filter(node -> node.name().equals(name)).findFirst().orElseThrow();
    }

    private void removeFromLoad(QueueTestNode node) {
        workload.changeNodes(() -> live.remove(node));
    }

    private void addToLoad(QueueTestNode node) {
        workload.changeNodes(() -> live.add(node));
    }

    private static void acceptWhileRunning(TestWorker worker, Set<Integer> received, AtomicLong lastReceived) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                WorkItem item = worker.poll(500);
                if (item != null) {
                    received.add(TestWorker.value(item));
                    lastReceived.set(System.currentTimeMillis());
                    // A failed accept leaves it unknown whether the item is done; one that is not comes back later
                    worker.settle(QueueWorker::accept, item).toCompletionStage().toCompletableFuture()
                          .handle((v, e) -> e == null).get(30, TimeUnit.SECONDS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertEveryShardHasAWorkloadKey() {
        Set<Integer> shards = IntStream.range(0, 16)
                                       .mapToObj(i -> QueueLog.shardOf("key-" + i, SHARDS))
                                       .collect(Collectors.toSet());
        assertEquals(SHARDS, shards.size(), "the workload's keys miss a shard, so a test relying on every shard receiving appends is void");
    }
}
