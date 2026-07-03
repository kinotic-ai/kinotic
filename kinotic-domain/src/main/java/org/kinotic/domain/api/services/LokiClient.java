package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.kinotic.domain.api.model.log.LogQuery;
import reactor.core.publisher.Flux;

/**
 * Transport to the Loki HTTP API: historical {@code query_range} requests and live {@code tail} over a
 * WebSocket. Responses are returned as raw {@link Buffer}s for passthrough to the caller. Each request
 * carries the given organization id as the Loki {@code X-Scope-OrgID} tenant header.
 */
public interface LokiClient {

    /**
     * Runs a Loki {@code query_range} and returns the raw response body.
     *
     * @param orgId the Loki tenant ({@code X-Scope-OrgID})
     * @param query the LogQL selector, time range, and limit
     * @return a {@link Future} of the raw Loki response body
     */
    Future<Buffer> queryRange(String orgId, LogQuery query);

    /**
     * Opens a Loki {@code tail} WebSocket and emits each tail frame's raw bytes. The WebSocket is closed
     * when the returned {@link Flux} is cancelled or completed.
     *
     * @param orgId the Loki tenant ({@code X-Scope-OrgID})
     * @param query the LogQL query to follow
     * @return a {@link Flux} of raw Loki tail frames
     */
    Flux<Buffer> tail(String orgId, String query);
}
