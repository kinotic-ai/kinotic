package org.kinotic.management.api.services.telemetry;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.model.telemetry.MetricQuery;
import org.kinotic.management.api.model.telemetry.TraceQuery;

/**
 * Queries the traces and metrics that the workloads of an organization exported: an organization
 * participant reads its own organization's, a system participant reads any organization's, or the
 * platform's own when it names no organization. Every method returns the raw backend response bytes;
 * the caller parses Tempo's and Prometheus's wire formats.
 */
@Publish
@AuthzResource(value = AuthzUtil.ORGANIZATION_TYPE, objectId = "{@organizationId}")
public interface TelemetryService {

    /**
     * Searches the traces matching a TraceQL query over a time range.
     *
     * @param query the {@link TraceQuery} naming the organization, query, time range, and limit
     * @return a {@link Future} emitting the raw Tempo {@code search} response
     */
    @AuthzCheck(permission = "can_view_telemetry")
    Future<Buffer> searchTraces(TraceQuery query);

    /**
     * Returns one trace with every span it holds.
     *
     * @param organizationId the organization the trace belongs to; null for the platform's own,
     *                       which only a system participant may read
     * @param traceId        the hex trace id
     * @return a {@link Future} emitting the raw Tempo trace response, OTLP JSON
     */
    @AuthzCheck(permission = "can_view_telemetry")
    Future<Buffer> findTrace(String organizationId, String traceId);

    /**
     * Evaluates a PromQL expression across a time range.
     *
     * @param query the {@link MetricQuery} naming the organization, expression, time range, and step
     * @return a {@link Future} emitting the raw Prometheus {@code query_range} response
     */
    @AuthzCheck(permission = "can_view_telemetry")
    Future<Buffer> queryMetrics(MetricQuery query);
}
