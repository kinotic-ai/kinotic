package org.kinotic.management.api.services.telemetry;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import reactor.core.publisher.Flux;

/**
 * Transport to the Loki HTTP API: historical {@code query_range} requests, live {@code tail} over a
 * WebSocket, and {@code delete} requests. Responses are returned as raw {@link Buffer}s for passthrough
 * to the caller. Each request carries the given tenant as the Loki {@code X-Scope-OrgID} header.
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

    /**
     * Asks Loki to delete every log entry the query selects within the time range. The entries leave
     * query results as soon as the request is accepted, and Loki's compactor removes them from storage
     * later, so the future completes once the request is accepted.
     *
     * @param tenant the Loki tenant ({@code X-Scope-OrgID})
     * @param query  the LogQL selector of the log streams to delete from
     * @param start  start of the time range, epoch milliseconds (inclusive)
     * @param end    end of the time range, epoch milliseconds (inclusive), not in the future
     * @return a {@link Future} that completes once Loki has accepted the request
     */
    Future<Void> delete(String tenant, String query, long start, long end);
}
