package org.kinotic.management.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * Transport to the Tempo HTTP API: TraceQL {@code search} requests and trace lookups by id. Responses
 * are returned as raw {@link Buffer}s for passthrough to the caller. Each request carries the given
 * tenant as the Tempo {@code X-Scope-OrgID} header.
 */
public interface TempoClient {

    /**
     * Runs a Tempo {@code search} and returns the raw response body.
     *
     * @param tenant the Tempo tenant ({@code X-Scope-OrgID})
     * @param query  the TraceQL query selecting the traces to return
     * @param start  start of the time range, epoch milliseconds (inclusive)
     * @param end    end of the time range, epoch milliseconds (inclusive)
     * @param limit  maximum number of traces to return
     * @return a {@link Future} of the raw Tempo response body
     */
    Future<Buffer> search(String tenant, String query, long start, long end, int limit);

    /**
     * Fetches one trace by id and returns the raw response body: the trace's spans as OTLP JSON.
     *
     * @param tenant  the Tempo tenant ({@code X-Scope-OrgID})
     * @param traceId the hex trace id
     * @return a {@link Future} of the raw Tempo response body
     */
    Future<Buffer> findTrace(String tenant, String traceId);
}
