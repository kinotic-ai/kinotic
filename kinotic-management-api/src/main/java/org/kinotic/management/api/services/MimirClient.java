package org.kinotic.management.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * Transport to the Mimir HTTP API: Prometheus {@code query_range} requests. Responses are returned as
 * raw {@link Buffer}s for passthrough to the caller. Each request carries the given tenant as the
 * Mimir {@code X-Scope-OrgID} header.
 */
public interface MimirClient {

    /**
     * Runs a Prometheus {@code query_range} and returns the raw response body.
     *
     * @param tenant the Mimir tenant ({@code X-Scope-OrgID})
     * @param query  the PromQL expression to evaluate
     * @param start  start of the time range, epoch milliseconds (inclusive)
     * @param end    end of the time range, epoch milliseconds (inclusive)
     * @param step   seconds between evaluated points
     * @return a {@link Future} of the raw Mimir response body
     */
    Future<Buffer> queryRange(String tenant, String query, long start, long end, long step);
}
