package org.kinotic.os.api.services;

import io.vertx.core.buffer.Buffer;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.model.log.LogQuery;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;

/**
 * Streams and queries container logs stored in Loki, scoped to the authenticated participant's organization.
 * Both methods return the raw Loki response bytes; the caller parses Loki's wire format.
 */
@Publish
public interface LogService {

    /**
     * Opens a live tail of logs matching the given LogQL query. Each emitted element is a raw Loki tail
     * frame, and the stream stays open until the caller unsubscribes.
     *
     * @param query the LogQL query selecting the log streams to follow
     * @return a {@link Flux} emitting raw Loki tail frames
     */
    Flux<Buffer> tail(String query);

    /**
     * Returns historical logs for the given query and time range.
     *
     * @param query the {@link LogQuery} describing the LogQL selector, time range, and limit
     * @return a {@link CompletableFuture} emitting the raw Loki {@code query_range} response
     */
    CompletableFuture<Buffer> history(LogQuery query);
}
