package org.kinotic.queue.internal;

import io.vertx.core.Future;
import org.kinotic.queue.api.model.QueueRecord;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Helpers shared by the queue tests: payloads carrying an int, and waiting on Vert.x futures.
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

    public static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(60, TimeUnit.SECONDS);
    }
}
