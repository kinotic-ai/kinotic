import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { TraceQuery } from '@/api/model/telemetry/TraceQuery'
import type { MetricQuery } from '@/api/model/telemetry/MetricQuery'

/**
 * Queries the traces and metrics that the workloads of an organization exported: an organization
 * participant reads its own organization's, a system participant reads any organization's, or the
 * platform's own when no organization is named. Every method yields the raw backend response
 * bytes; the caller parses Tempo's and Prometheus's wire formats.
 */
export interface ITelemetryService {

    /**
     * Searches the traces matching a TraceQL query over a time range, as the raw Tempo search
     * response.
     * @param query the organization, query, time range, and limit
     */
    searchTraces(query: TraceQuery): Promise<Uint8Array>

    /**
     * Returns one trace with every span it holds, as the raw Tempo trace response (OTLP JSON).
     * @param organizationId the organization the trace belongs to; null for the caller's own, or
     *        the platform's for a system participant
     * @param traceId the hex trace id
     */
    findTrace(organizationId: string | null, traceId: string): Promise<Uint8Array>

    /**
     * Evaluates a PromQL expression across a time range, as the raw Prometheus query_range
     * response.
     * @param query the organization, expression, time range, and step
     */
    queryMetrics(query: MetricQuery): Promise<Uint8Array>
}

export class TelemetryService implements ITelemetryService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.TelemetryService`)
    }

    public searchTraces(query: TraceQuery): Promise<Uint8Array> {
        return this.serviceProxy.invoke('searchTraces', [query])
    }

    public findTrace(organizationId: string | null, traceId: string): Promise<Uint8Array> {
        return this.serviceProxy.invoke('findTrace', [organizationId, traceId])
    }

    public queryMetrics(query: MetricQuery): Promise<Uint8Array> {
        return this.serviceProxy.invoke('queryMetrics', [query])
    }
}
