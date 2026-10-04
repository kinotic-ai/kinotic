package org.kinotic.queue.internal;

import io.vertx.core.Future;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.internal.log.QueueLog;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Helpers shared by the queue tests: payloads carrying an int, keys of a given shard, and waiting on Vert.x futures.
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

    public static <T> T await(Future<T> future) throws Exception {
        return awaitWithin(future, 60);
    }

    public static <T> T awaitWithin(Future<T> future, long seconds) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(seconds, TimeUnit.SECONDS);
    }
}
