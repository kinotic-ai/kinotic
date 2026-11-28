import { MANAGEMENT_API_ZONE } from '@/api/Zones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { TraceQuery } from '@/api/model/telemetry/TraceQuery'
import type { MetricQuery } from '@/api/model/telemetry/MetricQuery'
import { parseJsonBytes } from '@/internal/parseJsonBytes'

/**
 * Queries the traces and metrics that the workloads of an organization exported: an organization
 * participant reads its own organization's, a system participant reads any organization's, or the
 * system's own when it names no organization. Every method yields the backend's own response,
 * parsed from JSON: the caller reads Tempo's and Prometheus's wire formats.
 */
export interface ITelemetryService {

    /**
     * Searches the traces matching a TraceQL query over a time range, as the Tempo search
     * response: {traces: [{traceID, rootServiceName, rootTraceName, startTimeUnixNano, durationMs, spanSets}]}.
     * @param query the organization, query, time range, and limit
     */
    searchTraces(query: TraceQuery): Promise<any>

    /**
     * Returns one trace with every span it holds within a time range, as the Tempo trace response:
     * OTLP JSON, whose top-level resourceSpans Tempo names batches. The range should cover the whole
     * trace, as the trace search reported it: spans outside it may be left out.
     * @param organizationId the organization the trace belongs to; null for the system's own,
     *        which only a system participant may read
     * @param traceId the hex trace id
     * @param start start of the time range, epoch milliseconds (inclusive)
     * @param end end of the time range, epoch milliseconds (inclusive)
     */
    findTrace(organizationId: string | null, traceId: string, start: number, end: number): Promise<any>

    /**
     * Evaluates a PromQL expression across a time range, as the Prometheus query_range response:
     * {status, data: {resultType: 'matrix', result: [{metric, values: [[seconds, "value"]]}]}}.
     * @param query the organization, expression, time range, and step
     */
    queryMetrics(query: MetricQuery): Promise<any>
}

export class TelemetryService implements ITelemetryService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.telemetry.TelemetryService`)
    }

    public async searchTraces(query: TraceQuery): Promise<any> {
        return parseJsonBytes(await this.serviceProxy.invoke('searchTraces', [query]))
    }

    public async findTrace(organizationId: string | null, traceId: string, start: number, end: number): Promise<any> {
        return parseJsonBytes(await this.serviceProxy.invoke('findTrace', [organizationId, traceId, start, end]))
    }

    public async queryMetrics(query: MetricQuery): Promise<any> {
        return parseJsonBytes(await this.serviceProxy.invoke('queryMetrics', [query]))
    }
}
