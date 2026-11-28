package org.kinotic.management.api.services.telemetry;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.telemetry.MetricQuery;
import org.kinotic.management.api.model.telemetry.TraceQuery;

/**
 * Queries the traces and metrics that the workloads of an organization exported: an organization
 * participant reads its own organization's, a system participant reads any organization's, or the
 * system's own when it names no organization. Every method returns the raw backend response bytes;
 * the caller parses Tempo's and Prometheus's wire formats.
 */
@Publish
public interface TelemetryService {

    /**
     * Searches the traces matching a TraceQL query over a time range.
     *
     * @param query the {@link TraceQuery} naming the organization, query, time range, and limit
     * @return a {@link Future} emitting the raw Tempo {@code search} response
     */
    Future<Buffer> searchTraces(TraceQuery query);

    /**
     * Returns one trace with every span it holds within a time range. The range should cover the
     * whole trace, as the trace search reported it: spans outside it may be left out.
     *
     * @param organizationId the organization the trace belongs to; null for the system's own,
     *                       which only a system participant may read
     * @param traceId        the hex trace id
     * @param start          start of the time range, epoch milliseconds (inclusive)
     * @param end            end of the time range, epoch milliseconds (inclusive)
     * @return a {@link Future} emitting the raw Tempo trace response, OTLP JSON
     */
    Future<Buffer> findTrace(String organizationId, String traceId, long start, long end);

    /**
     * Evaluates a PromQL expression across a time range.
     *
     * @param query the {@link MetricQuery} naming the organization, expression, time range, and step
     * @return a {@link Future} emitting the raw Prometheus {@code query_range} response
     */
    Future<Buffer> queryMetrics(MetricQuery query);
}
