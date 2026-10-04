package org.kinotic.queue.internal;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.model.StartPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kinotic.queue.internal.QueueTestSupport.payload;
import static org.kinotic.queue.internal.QueueTestSupport.value;

/**
 * Appends records in the background through randomly chosen live nodes while a test injects faults, and remembers
 * which appends were acknowledged. Values are appended in increasing order, one at a time, spread over a fixed set of
 * keys, so each key's records were appended in increasing value order.
 */
@Slf4j
public final class QueueWorkload implements AutoCloseable {

    private static final int KEYS = 16;
    // Longer than the queue client retries an append, so every append ends with an answer
    private static final long APPEND_WAIT_SECONDS = 45;

    private final String queue;
    private final List<QueueTestNode> live;
    private final ReentrantLock appending = new ReentrantLock();
    private final Set<Integer> attempted = ConcurrentHashMap.newKeySet();
    private final Set<Integer> acknowledged = ConcurrentHashMap.newKeySet();
    private final Thread producer;
    private volatile boolean running = true;
    private volatile int nextValue;

    /**
     * @param live the nodes appends go through; change it only through {@link #changeNodes}
     */
    public QueueWorkload(String queue, List<QueueTestNode> live, int firstValue) {
        this.queue = queue;
        this.live = live;
        this.nextValue = firstValue;
        this.producer = Thread.ofPlatform().name("queue-workload").start(this::produce);
    }

    /**
     * Changes the nodes appends go through between two appends, so no append is in flight through a node being removed.
     */
    public void changeNodes(Runnable change) {
        appending.lock();
        try {
            change.run();
        } finally {
            appending.unlock();
        }
    }

    /**
     * Waits until at least {@code count} more appends were acknowledged.
     */
    public void awaitAcknowledged(int count) throws InterruptedException {
        int target = acknowledged.size() + count;
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(120);
        while (acknowledged.size() < target) {
            assertTrue(System.currentTimeMillis() < deadline, "only " + acknowledged.size() + " of " + target + " appends acknowledged");
            Thread.sleep(20);
        }
    }

    /**
     * Stops appending once the append in flight ends.
     */
    @Override
    public void close() throws InterruptedException {
        running = false;
        producer.join(TimeUnit.SECONDS.toMillis(APPEND_WAIT_SECONDS + 15));
    }

    public Set<Integer> acknowledged() {
        return Set.copyOf(acknowledged);
    }

    public int nextValue() {
        return nextValue;
    }

    /**
     * Reads the whole queue with a new consumer on the node and checks that every acknowledged record is delivered, that
     * every delivered record was appended and is in the queue once, and that each key's acknowledged records arrive in
     * the order they were appended. An append that failed may or may not be delivered, and may land after records
     * appended later, since the failure leaves it unknown when or whether it was written.
     */
    public void verifyDelivered(QueueTestNode node, String consumerName) throws Exception {
        TestSubscriber subscriber = TestSubscriber.subscribe(node.vertx(), node.queueService(), queue, consumerName, StartPosition.EARLIEST);
        Set<Integer> delivered = new HashSet<>();
        Map<String, Integer> lastFirstDeliveryByKey = new HashMap<>();
        List<Integer> missing = new ArrayList<>(acknowledged);
        while (!delivered.containsAll(acknowledged)) {
            QueueRecord record = subscriber.next();
            int value = value(record);
            assertTrue(attempted.contains(value), "delivered " + value + ", which was never appended");
            assertTrue(delivered.add(value), "the queue holds " + value + " twice");
            if (acknowledged.contains(value)) {
                Integer last = lastFirstDeliveryByKey.put(record.key(), value);
                assertTrue(last == null || last < value, "key " + record.key() + " delivered " + value + " after " + last);
            }
        }
        missing.removeAll(delivered);
        assertTrue(missing.isEmpty(), "acknowledged but never delivered: " + missing);
        subscriber.close();
    }

    private void produce() {
        while (running) {
            appending.lock();
            try {
                appendNext();
            } finally {
                appending.unlock();
            }
        }
    }

    private void appendNext() {
        int value = nextValue;
        QueueTestNode node = live.get(ThreadLocalRandom.current().nextInt(live.size()));
        attempted.add(value);
        try {
            QueueTestSupport.awaitWithin(node.queueService().append(queue, "key-" + (value % KEYS), payload(value)), APPEND_WAIT_SECONDS);
            acknowledged.add(value);
        } catch (Exception e) {
            log.debug("Appending {} through {} failed", value, node.name(), e);
        }
        nextValue = value + 1;
    }
}
