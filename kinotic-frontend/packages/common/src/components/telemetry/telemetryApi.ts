import { Kinotic } from '@kinotic-ai/core'
import { parseJsonBytes } from '../../util/helpers'
import type { MetricSeries } from './MetricSeries'
import type { TimeRange } from './TimeRange'
import type { TraceSpan } from './TraceSpan'
import type { TraceSpanEvent } from './TraceSpanEvent'
import type { TraceSummary } from './TraceSummary'

/** The spans of an application's, or a whole organization's, services, as Tempo derives them into metrics. */
const SPAN_METRIC_CALLS = 'traces_spanmetrics_calls_total'
const SPAN_METRIC_LATENCY = 'traces_spanmetrics_latency_bucket'

/** How many points a range query aims to return, whatever its range. */
const POINTS_PER_RANGE = 120

/**
 * The time ranges the telemetry and log views offer; the second is the one they open on.
 * The label reads in a dropdown, the short label on a segmented button.
 */
export const TIME_RANGE_PRESETS: Array<{ label: string; shortLabel: string; ms: number }> = [
    { label: 'Last 15 minutes', shortLabel: '15m', ms: 15 * 60_000 },
    { label: 'Last hour', shortLabel: '1h', ms: 60 * 60_000 },
    { label: 'Last 6 hours', shortLabel: '6h', ms: 6 * 60 * 60_000 },
    { label: 'Last 24 hours', shortLabel: '24h', ms: 24 * 60 * 60_000 },
    { label: 'Last 7 days', shortLabel: '7d', ms: 7 * 24 * 60 * 60_000 }
]

export function rangeEndingNow(ms: number): TimeRange {
    const end = Date.now()
    return { start: end - ms, end }
}

/** What the trace search narrows by; each set filter becomes one TraceQL clause. */
export interface TraceFilters {
    applicationId: string | null
    service: string
    spanName: string
    onlyErrors: boolean
    minDurationMs: number | null
}

/**
 * The TraceQL selecting the traces the filters describe. String values are JSON-quoted, which
 * is also TraceQL's escaping, so a filter cannot break out of its clause.
 */
export function traceQl(filters: TraceFilters): string {
    const clauses = [
        ...(filters.applicationId ? [`resource.application_id = ${JSON.stringify(filters.applicationId)}`] : []),
        ...(filters.service.trim() ? [`resource.service.name = ${JSON.stringify(filters.service.trim())}`] : []),
        ...(filters.spanName.trim() ? [`name = ${JSON.stringify(filters.spanName.trim())}`] : []),
        ...(filters.onlyErrors ? ['status = error'] : []),
        ...(filters.minDurationMs ? [`duration > ${Math.round(filters.minDurationMs)}ms`] : [])
    ]
    return clauses.length > 0 ? `{ ${clauses.join(' && ')} }` : '{}'
}

// Seconds between the points of a range query, sized so a chart gets about POINTS_PER_RANGE
function stepSeconds(range: TimeRange): number {
    return Math.max(15, Math.round((range.end - range.start) / 1000 / POINTS_PER_RANGE))
}

// A rate needs several samples in its window, so it spans a few steps and never less than a minute
function rateWindow(range: TimeRange): string {
    return `${Math.max(60, 4 * stepSeconds(range))}s`
}

function selector(applicationId: string | null, extra: string[] = []): string {
    const labels = [
        ...(applicationId ? [`application_id=${JSON.stringify(applicationId)}`] : []),
        ...extra
    ]
    return labels.length > 0 ? `{${labels.join(', ')}}` : ''
}

/**
 * The RED queries — rate, errors, duration — of an application's services, or of every
 * service in the organization when no application is named, from the span metrics Tempo
 * derives from the traces the node ships.
 */
export function redQueries(applicationId: string | null, range: TimeRange): { requests: string; errors: string; latencyP95: string } {
    const window = rateWindow(range)
    const calls = `rate(${SPAN_METRIC_CALLS}${selector(applicationId)}[${window}])`
    const failedCalls = `rate(${SPAN_METRIC_CALLS}${selector(applicationId, ['status_code="STATUS_CODE_ERROR"'])}[${window}])`
    return {
        requests: `sum by (service) (${calls})`,
        errors: `sum by (service) (${failedCalls}) / sum by (service) (${calls})`,
        latencyP95: `histogram_quantile(0.95, sum by (le, service) (rate(${SPAN_METRIC_LATENCY}${selector(applicationId)}[${window}])))`
    }
}

/** Searches the organization's traces: the first ones Tempo finds in the range, up to the limit, in no particular order. */
export async function searchTraces(organizationId: string | null, query: string, range: TimeRange, limit: number): Promise<TraceSummary[]> {
    // Raw Tempo search response: {traces: [{traceID, rootServiceName, rootTraceName, startTimeUnixNano, durationMs, spanSets}]}
    const body = parseJsonBytes(await Kinotic.telemetry.searchTraces({ organizationId, query, start: range.start, end: range.end, limit }))
    return ((body?.traces ?? []) as any[]).map(parseTraceSummary)
}

function parseTraceSummary(trace: any): TraceSummary {
    const spanSets: any[] = trace.spanSets ?? []
    return {
        traceId: String(trace.traceID ?? ''),
        rootService: trace.rootServiceName ?? '',
        rootName: trace.rootTraceName ?? '',
        startMs: Number(trace.startTimeUnixNano ?? 0) / 1_000_000,
        durationMs: Number(trace.durationMs ?? 0),
        matchedSpans: spanSets.reduce((count, set) => count + Number(set.matched ?? set.spans?.length ?? 0), 0)
    }
}

/** Fetches one trace as its spans in waterfall order: each span's children under it, siblings by start. */
export async function fetchTrace(organizationId: string | null, traceId: string): Promise<TraceSpan[]> {
    // Raw Tempo trace response: OTLP JSON, whose top-level resourceSpans list Tempo names batches
    const body = parseJsonBytes(await Kinotic.telemetry.findTrace(organizationId, traceId))
    return orderForWaterfall(collectSpans(body?.batches ?? []))
}

function collectSpans(batches: any[]): TraceSpan[] {
    const spans: TraceSpan[] = []
    for (const batch of batches) {
        const resource = attributesOf(batch.resource?.attributes)
        const service = resource['service.name'] ?? ''
        for (const scope of batch.scopeSpans ?? []) {
            for (const span of scope.spans ?? []) {
                const startMs = Number(span.startTimeUnixNano ?? 0) / 1_000_000
                const endMs = Number(span.endTimeUnixNano ?? 0) / 1_000_000
                spans.push({
                    spanId: idToHex(span.spanId),
                    parentSpanId: span.parentSpanId ? idToHex(span.parentSpanId) : null,
                    name: span.name ?? '',
                    service,
                    kind: kindOf(span.kind),
                    startMs,
                    durationMs: Math.max(0, endMs - startMs),
                    error: span.status?.code === 2 || span.status?.code === 'STATUS_CODE_ERROR',
                    statusMessage: span.status?.message ?? '',
                    attributes: attributesOf(span.attributes),
                    resource,
                    events: ((span.events ?? []) as any[]).map(eventOf),
                    depth: 0
                })
            }
        }
    }
    return spans
}

function orderForWaterfall(spans: TraceSpan[]): TraceSpan[] {
    const known = new Set(spans.map(span => span.spanId))
    // A span whose parent the trace does not hold (a partial trace) roots its own subtree
    const children = new Map<string | null, TraceSpan[]>()
    for (const span of spans) {
        const parent = span.parentSpanId !== null && known.has(span.parentSpanId) && span.parentSpanId !== span.spanId
            ? span.parentSpanId
            : null
        let siblings = children.get(parent)
        if (siblings === undefined) {
            children.set(parent, siblings = [])
        }
        siblings.push(span)
    }
    for (const siblings of children.values()) {
        siblings.sort((a, b) => a.startMs - b.startMs)
    }
    const ordered: TraceSpan[] = []
    const visited = new Set<string>()
    const visit = (parent: string | null, depth: number) => {
        for (const span of children.get(parent) ?? []) {
            // Malformed parent links can form a cycle; a span is placed once
            if (!visited.has(span.spanId)) {
                visited.add(span.spanId)
                span.depth = depth
                ordered.push(span)
                visit(span.spanId, depth + 1)
            }
        }
    }
    visit(null, 0)
    return ordered
}

function eventOf(event: any): TraceSpanEvent {
    return {
        name: event.name ?? '',
        timeMs: Number(event.timeUnixNano ?? 0) / 1_000_000,
        attributes: attributesOf(event.attributes)
    }
}

// OTLP JSON carries ids as base64 of the raw bytes; a hex id is passed through as it is
function idToHex(id: string): string {
    let ret: string
    if (/^[0-9a-f]+$/i.test(id) && (id.length === 16 || id.length === 32)) {
        ret = id.toLowerCase()
    } else {
        try {
            ret = Array.from(atob(id), char => char.charCodeAt(0).toString(16).padStart(2, '0')).join('')
        } catch {
            ret = id
        }
    }
    return ret
}

// The kind arrives as the protobuf enum name or its number, depending on the encoder
function kindOf(kind: unknown): string {
    const names = ['unspecified', 'internal', 'server', 'client', 'producer', 'consumer']
    let ret: string
    if (typeof kind === 'number') {
        ret = names[kind] ?? 'unspecified'
    } else if (typeof kind === 'string') {
        ret = kind.replace(/^SPAN_KIND_/, '').toLowerCase()
    } else {
        ret = 'unspecified'
    }
    return ret
}

// OTLP attributes are a list of {key, value: {stringValue | intValue | ...}} pairs
function attributesOf(list: any[] | undefined): Record<string, string> {
    const ret: Record<string, string> = {}
    for (const entry of list ?? []) {
        if (entry?.key !== undefined) {
            ret[entry.key] = anyValueToString(entry.value)
        }
    }
    return ret
}

function anyValueToString(value: any): string {
    let ret: string
    if (value === null || value === undefined) {
        ret = ''
    } else if (value.stringValue !== undefined) {
        ret = String(value.stringValue)
    } else if (value.intValue !== undefined) {
        ret = String(value.intValue)
    } else if (value.doubleValue !== undefined) {
        ret = String(value.doubleValue)
    } else if (value.boolValue !== undefined) {
        ret = String(value.boolValue)
    } else if (value.arrayValue !== undefined) {
        ret = `[${((value.arrayValue.values ?? []) as any[]).map(anyValueToString).join(', ')}]`
    } else if (value.kvlistValue !== undefined) {
        ret = JSON.stringify(attributesOf(value.kvlistValue.values))
    } else {
        ret = JSON.stringify(value)
    }
    return ret
}

/** Evaluates a PromQL expression over the range, one series per result. */
export async function queryMetrics(organizationId: string | null, query: string, range: TimeRange): Promise<MetricSeries[]> {
    // Raw Prometheus query_range response: {status, data: {resultType: 'matrix', result: [{metric, values: [[seconds, "value"]]}]}}
    const body = parseJsonBytes(await Kinotic.telemetry.queryMetrics({ organizationId, query, start: range.start, end: range.end, step: stepSeconds(range) }))
    if (body?.status !== 'success') {
        throw new Error(body?.error ?? 'Metric query failed')
    }
    return ((body.data?.result ?? []) as any[]).map(result => ({
        name: seriesName(result.metric ?? {}),
        points: ((result.values ?? []) as Array<[number | string, string]>).map(([seconds, value]) => [Number(seconds) * 1000, Number(value)] as [number, number])
    }))
}

// A series is named by its labels: the one label's value when there is one, every pair otherwise
function seriesName(metric: Record<string, string>): string {
    const { __name__, ...labels } = metric
    const entries = Object.entries(labels)
    let ret: string
    if (entries.length === 0) {
        ret = __name__ ?? 'value'
    } else if (entries.length === 1) {
        ret = entries[0]![1]
    } else {
        ret = entries.map(([key, value]) => `${key}=${value}`).join(', ')
    }
    return ret
}
