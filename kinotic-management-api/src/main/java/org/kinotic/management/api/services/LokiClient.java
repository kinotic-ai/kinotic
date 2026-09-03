package org.kinotic.management.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import reactor.core.publisher.Flux;

/**
 * Transport to the Loki HTTP API: historical {@code query_range} requests and live {@code tail} over a
 * WebSocket. Responses are returned as raw {@link Buffer}s for passthrough to the caller. Each request
 * carries the given tenant as the Loki {@code X-Scope-OrgID} header.
 */
public interface LokiClient {

    /**
     * Runs a Loki {@code query_range} and returns the raw response body.
     *
     * @param tenant the Loki tenant ({@code X-Scope-OrgID})
     * @param query  the LogQL query selecting the log streams to return
     * @param start  start of the time range, epoch milliseconds (inclusive)
     * @param end    end of the time range, epoch milliseconds (inclusive)
     * @param limit  maximum number of log entries to return
     * @return a {@link Future} of the raw Loki response body
     */
    Future<Buffer> queryRange(String tenant, String query, long start, long end, int limit);

    /**
     * Opens a Loki {@code tail} WebSocket and emits each tail frame's raw bytes. The WebSocket is closed
     * when the returned {@link Flux} is cancelled or completed.
     *
     * @param tenant the Loki tenant ({@code X-Scope-OrgID})
     * @param query  the LogQL query to follow
     * @return a {@link Flux} of raw Loki tail frames
     */
    Flux<Buffer> tail(String tenant, String query);
}
