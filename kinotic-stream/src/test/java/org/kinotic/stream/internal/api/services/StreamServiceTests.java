package org.kinotic.stream.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.stream.api.config.KinoticStreamProperties;
import org.kinotic.stream.api.model.StartPosition;
import org.kinotic.stream.api.model.StreamDefinition;
import org.kinotic.stream.api.model.StreamPosition;
import org.kinotic.stream.api.model.StreamRecord;
import org.kinotic.stream.api.services.StreamSubscription;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral tests for {@link DefaultStreamService} against real Chronicle Queue files in a temporary directory:
 * per-key ordering, at-least-once resume after a restart, start positions, and backpressure.
 */
public class StreamServiceTests {

    @TempDir
    private Path dataDirectory;

    private Vertx vertx;
    private DefaultStreamService service;

    @BeforeEach
    public void setUp() {
        vertx = Vertx.vertx();
        service = newService();
    }

    @AfterEach
    public void tearDown() throws Exception {
        service.close();
        await(vertx.close());
    }

    @Test
    public void createStreamIfNotExistKeepsTheOriginalShardCount() throws Exception {
        await(service.createStreamIfNotExist(new StreamDefinition("orders", 4)));

        StreamDefinition stored = await(service.createStreamIfNotExist(new StreamDefinition("orders", 8)));

        assertEquals(4, stored.shardCount());
    }

    @Test
    public void recordsWithTheSameKeyAreDeliveredInAppendOrder() throws Exception {
        await(service.createStreamIfNotExist(new StreamDefinition("orders", 4)));
        for (int i = 0; i < 50; i++) {
            await(service.append("orders", "customer-" + (i % 5), payload(i)));
        }

        Subscriber subscriber = subscribe("billing", StartPosition.EARLIEST);
        Map<String, List<Integer>> byKey = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            StreamRecord record = subscriber.next();
            byKey.computeIfAbsent(record.key(), k -> new ArrayList<>()).add(value(record));
        }

        assertEquals(5, byKey.size());
        byKey.forEach((key, values) -> {
            int customer = Integer.parseInt(key.substring("customer-".length()));
            for (int i = 0; i < values.size(); i++) {
                assertEquals(customer + i * 5, values.get(i), "out of order for " + key);
            }
        });
    }

    @Test
    public void consumerResumesAfterItsLastCommitAcrossARestart() throws Exception {
        await(service.createStreamIfNotExist(new StreamDefinition("orders", 1)));
        for (int i = 0; i < 10; i++) {
            StreamPosition position = await(service.append("orders", "key", payload(i)));
            assertEquals(i, position.offset());
        }
        Subscriber first = subscribe("billing", StartPosition.EARLIEST);
        StreamRecord committed = null;
        for (int i = 0; i < 6; i++) {
            StreamRecord record = first.next();
            if (i == 3) {
                committed = record;
            }
        }
        await(first.commit(committed));
        await(first.close());

        service.close();
        service = newService();
        // Offsets continue where the reopened shard left off
        assertEquals(10, await(service.append("orders", "key", payload(10))).offset());

        Subscriber resumed = subscribe("billing", StartPosition.EARLIEST);
        for (int i = 4; i <= 10; i++) {
            assertEquals(i, value(resumed.next()));
        }
        assertNull(resumed.poll(200));
    }

    @Test
    public void latestStartsWithTheNextAppendedRecord() throws Exception {
        await(service.createStreamIfNotExist(new StreamDefinition("orders", 2)));
        for (int i = 0; i < 5; i++) {
            await(service.append("orders", "key-" + i, payload(i)));
        }

        Subscriber subscriber = subscribe("audit", StartPosition.LATEST);
        assertNull(subscriber.poll(200));
        await(service.append("orders", "key-5", payload(5)));

        assertEquals(5, value(subscriber.next()));
    }

    @Test
    public void pausedSubscriptionDeliversOnlyWhatIsFetched() throws Exception {
        await(service.createStreamIfNotExist(new StreamDefinition("orders", 1)));
        Subscriber subscriber = subscribe("billing", StartPosition.EARLIEST);
        subscriber.onContext(StreamSubscription::pause);
        for (int i = 0; i < 5; i++) {
            await(service.append("orders", "key", payload(i)));
        }
        assertNull(subscriber.poll(200));

        subscriber.onContext(s -> s.fetch(2));

        assertEquals(0, value(subscriber.next()));
        assertEquals(1, value(subscriber.next()));
        assertNull(subscriber.poll(200));
    }

    @Test
    public void commitOfARecordNotYetDeliveredFails() throws Exception {
        await(service.createStreamIfNotExist(new StreamDefinition("orders", 1)));
        await(service.append("orders", "key", payload(0)));
        Subscriber subscriber = subscribe("billing", StartPosition.EARLIEST);
        StreamRecord delivered = subscriber.next();
        StreamRecord ahead = new StreamRecord(new StreamPosition("orders", 0, delivered.position().offset() + 1),
                                              "key", payload(1));

        ExecutionException e = assertThrows(ExecutionException.class, () -> await(subscriber.commit(ahead)));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    @Test
    public void appendToAMissingOrInvalidStreamFails() {
        ExecutionException missing = assertThrows(ExecutionException.class,
                                                  () -> await(service.append("missing", "key", payload(0))));
        assertInstanceOf(IllegalArgumentException.class, missing.getCause());

        ExecutionException traversal = assertThrows(ExecutionException.class,
                                                    () -> await(service.append("../outside", "key", payload(0))));
        assertInstanceOf(IllegalArgumentException.class, traversal.getCause());
    }

    private DefaultStreamService newService() {
        KinoticStreamProperties properties = new KinoticStreamProperties();
        properties.getStream().setDataDirectory(dataDirectory.toString());
        return new DefaultStreamService(properties, vertx);
    }

    private Subscriber subscribe(String consumerName, StartPosition startPosition) throws Exception {
        Context context = vertx.getOrCreateContext();
        LinkedBlockingQueue<StreamRecord> received = new LinkedBlockingQueue<>();
        CompletableFuture<StreamSubscription> subscribed = new CompletableFuture<>();
        context.runOnContext(v -> service.subscribe("orders", consumerName, startPosition)
                                         .onSuccess(subscription -> {
                                             subscription.handler(received::add);
                                             subscribed.complete(subscription);
                                         })
                                         .onFailure(subscribed::completeExceptionally));
        return new Subscriber(context, subscribed.get(10, TimeUnit.SECONDS), received);
    }

    private static byte[] payload(int value) {
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    private static int value(StreamRecord record) {
        return Integer.parseInt(new String(record.payload(), StandardCharsets.UTF_8));
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    /**
     * Drives a subscription from the test thread, calling it on its own context as its contract requires.
     */
    private record Subscriber(Context context, StreamSubscription subscription, LinkedBlockingQueue<StreamRecord> received) {

        StreamRecord next() throws InterruptedException {
            StreamRecord ret = received.poll(10, TimeUnit.SECONDS);
            assertTrue(ret != null, "no record delivered within 10 seconds");
            return ret;
        }

        StreamRecord poll(long millis) throws InterruptedException {
            return received.poll(millis, TimeUnit.MILLISECONDS);
        }

        void onContext(java.util.function.Consumer<StreamSubscription> action) throws Exception {
            CompletableFuture<Void> done = new CompletableFuture<>();
            context.runOnContext(v -> {
                action.accept(subscription);
                done.complete(null);
            });
            done.get(10, TimeUnit.SECONDS);
        }

        Future<Void> commit(StreamRecord record) throws Exception {
            CompletableFuture<Future<Void>> ret = new CompletableFuture<>();
            context.runOnContext(v -> ret.complete(subscription.commit(record)));
            return ret.get(10, TimeUnit.SECONDS);
        }

        Future<Void> close() throws Exception {
            CompletableFuture<Future<Void>> ret = new CompletableFuture<>();
            context.runOnContext(v -> ret.complete(subscription.close()));
            return ret.get(10, TimeUnit.SECONDS);
        }
    }
}
