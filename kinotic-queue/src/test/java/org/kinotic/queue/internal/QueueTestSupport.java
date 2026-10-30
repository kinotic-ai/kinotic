package org.kinotic.queue.internal;

import io.vertx.core.Future;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.services.QueueService;
import org.kinotic.queue.internal.log.QueueLog;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Helpers shared by the queue tests: payloads carrying an int, keys of a given shard, appending a range of values, and
 * waiting on Vert.x futures.
 */
public final class QueueTestSupport {

    private QueueTestSupport() {
    }

    public static byte[] payload(int value) {
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    public static int value(QueueRecord record) {
        return Integer.parseInt(new String(record.payload(), StandardCharsets.UTF_8));
    }

    /**
     * @return a key that belongs to the shard
     */
    public static String keyOf(int shard, int shardCount) {
        int i = 0;
        while (QueueLog.shardOf("shard-key-" + i, shardCount) != shard) {
            i++;
        }
        return "shard-key-" + i;
    }

    /**
     * Appends the values from {@code from} up to {@code to}, one at a time and all under the same key.
     */
    public static void appendAll(QueueService service, String queue, int from, int to) throws Exception {
        for (int i = from; i < to; i++) {
            await(service.append(queue, "key", payload(i)));
        }
    }

    /**
     * @return the values from {@code from} up to {@code to}
     */
    public static Set<Integer> range(int from, int to) {
        return IntStream.range(from, to).boxed().collect(Collectors.toSet());
    }

    /**
     * @return the future's result; throws the future's failure as it is
     */
    public static <T> T await(Future<T> future) throws TimeoutException {
        return awaitWithin(future, 60);
    }

    public static <T> T awaitWithin(Future<T> future, long seconds) throws TimeoutException {
        return future.await(seconds, TimeUnit.SECONDS);
    }
}
