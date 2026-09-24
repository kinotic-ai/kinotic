package org.kinotic.management.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.MetricQuery;
import org.kinotic.management.api.model.TraceQuery;
import org.kinotic.management.api.model.TrafficQuery;

/**
 * Queries the traces and metrics that the workloads of an organization exported, and the traffic its
 * callers sent through the gateway: an organization participant reads its own organization's, a system
 * participant reads any organization's, or the platform's own when it names no organization. Every
 * method returns the raw backend response bytes; the caller parses Tempo's and Prometheus's wire
 * formats.
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
     * Returns one trace with every span it holds.
     *
     * @param organizationId the organization the trace belongs to; null for the platform's own,
     *                       which only a system participant may read
     * @param traceId        the hex trace id
     * @return a {@link Future} emitting the raw Tempo trace response, OTLP JSON
     */
    Future<Buffer> findTrace(String organizationId, String traceId);

    /**
     * Evaluates a PromQL expression across a time range.
     *
     * @param query the {@link MetricQuery} naming the organization, expression, time range, and step
     * @return a {@link Future} emitting the raw Prometheus {@code query_range} response
     */
    Future<Buffer> queryMetrics(MetricQuery query);

    /**
     * Evaluates one signal of the invocations clients made through the gateway across a time range:
     * those whose callers act for an organization, summed over its applications, or for one of its
     * applications, or, for a system participant naming no organization, every invocation on the
     * platform.
     *
     * @param query the {@link TrafficQuery} naming the organization, application, signal, time range, and step
     * @return a {@link Future} emitting the raw Prometheus {@code query_range} response
     */
    Future<Buffer> queryTraffic(TrafficQuery query);
}
