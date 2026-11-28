import { Kinotic } from '@kinotic-ai/core'
import { OtlpSpanType, OtlpStatusCode } from '@kinotic-ai/management-api'
import type { OtlpAnyValue, OtlpKeyValue, OtlpResourceSpans, OtlpSpanEvent, OtlpSpanTypeName, TempoSearchTrace } from '@kinotic-ai/management-api'
import type { LocationQuery } from 'vue-router'
import type { MetricSeries } from './MetricSeries'
import type { TimeRange } from './TimeRange'
import type { TraceSpan } from './TraceSpan'
import type { TraceSpanEvent } from './TraceSpanEvent'
import type { TraceSummary } from './TraceSummary'

/** The spans of an application's, or a whole organization's, services, as Tempo derives them into metrics. */
const SPAN_METRIC_CALLS = 'traces_spanmetrics_calls_total'
const SPAN_METRIC_LATENCY = 'traces_spanmetrics_latency_bucket'
/** The span metrics of the calls a service handles, leaving out the calls it makes, such as to Elasticsearch. */
const SERVER_SPANS = 'span_kind="SPAN_KIND_SERVER"'

/**
 * How far a trace lookup reaches past the start and end the trace search reported, which come from
 * the blocks that answered the search rather than from every span of the trace.
 */
const TRACE_WINDOW_MARGIN_MS = 5 * 60_000
/**
 * The widest window a trace is looked up in, the day TelemetryService.findTrace accepts at most; also
 * the window a trace's page looks in when its link names none.
 */
const TRACE_LOOKUP_MAX_MS = 24 * 60 * 60_000

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
 * The RED queries — rate, errors, duration — of the calls an application's services handle,
 * or every service in the organization when no application is named, from the span metrics
 * Tempo derives from the traces the node ships.
 */
export function redQueries(applicationId: string | null, range: TimeRange): { requests: string; errors: string; latencyP95: string } {
    const window = rateWindow(range)
    const calls = `rate(${SPAN_METRIC_CALLS}${selector(applicationId, [SERVER_SPANS])}[${window}])`
    const failedCalls = `rate(${SPAN_METRIC_CALLS}${selector(applicationId, [SERVER_SPANS, 'status_code="STATUS_CODE_ERROR"'])}[${window}])`
    return {
        requests: `sum by (service) (${calls})`,
        errors: `sum by (service) (${failedCalls}) / sum by (service) (${calls})`,
        latencyP95: `histogram_quantile(0.95, sum by (le, service) (rate(${SPAN_METRIC_LATENCY}${selector(applicationId, [SERVER_SPANS])}[${window}])))`
    }
}

/** Searches the organization's traces: the first ones Tempo finds in the range, up to the limit, in no particular order. */
export async function searchTraces(organizationId: string | null, query: string, range: TimeRange, limit: number): Promise<TraceSummary[]> {
    const body = await Kinotic.telemetry.searchTraces({ organizationId, query, start: range.start, end: range.end, limit })
    return (body.traces ?? []).map(parseTraceSummary)
}

function parseTraceSummary(trace: TempoSearchTrace): TraceSummary {
    const spanSets = trace.spanSets ?? []
    return {
        traceId: trace.traceID,
        rootService: trace.rootServiceName ?? '',
        rootName: trace.rootTraceName ?? '',
        startMs: Number(trace.startTimeUnixNano ?? 0) / 1_000_000,
        durationMs: Number(trace.durationMs ?? 0),
        matchedSpans: spanSets.reduce((count, set) => count + Number(set.matched ?? set.spans?.length ?? 0), 0)
    }
}

/** The time range a trace from the search is looked up in: the trace's own, with a margin on both sides. */
export function traceWindow(trace: TraceSummary): TimeRange {
    return {
        start: trace.startMs - TRACE_WINDOW_MARGIN_MS,
        end: trace.startMs + trace.durationMs + TRACE_WINDOW_MARGIN_MS
    }
}

/** A trace window as the query of a link to the trace's page, which {@link traceWindowFromQuery} reads back. */
export function traceWindowQuery(range: TimeRange): Record<string, string> {
    return { start: String(Math.floor(range.start)), end: String(Math.ceil(range.end)) }
}

/**
 * The trace window a link to a trace's page carries in its query, or the day before now when the
 * link carries no window a trace can be looked up in, as a link typed or shared by hand may not.
 */
export function traceWindowFromQuery(query: LocationQuery): TimeRange {
    const isEpochMs = (value: unknown): value is string => typeof value === 'string' && /^\d+$/.test(value)
    let ret: TimeRange
    if (isEpochMs(query.start) && isEpochMs(query.end)
        && Number(query.start) >= 1000
        && Number(query.start) <= Number(query.end)
        && Number(query.end) - Number(query.start) <= TRACE_LOOKUP_MAX_MS) {
        ret = { start: Number(query.start), end: Number(query.end) }
    } else {
        ret = rangeEndingNow(TRACE_LOOKUP_MAX_MS)
    }
    return ret
}

/**
 * Fetches one trace as its spans in waterfall order: each span's children under it, siblings by start.
 * A trace whose root span is not within the range is looked up again over the day up to the range's
 * end; spans that began before that are not found.
 */
export async function fetchTrace(organizationId: string | null, traceId: string, range: TimeRange): Promise<TraceSpan[]> {
    const start = Math.floor(range.start)
    const end = Math.ceil(range.end)
    let spans = await fetchSpans(organizationId, traceId, start, end)
    // The search reports the start it saw within its own range, so a trace that began before that range
    // comes back without its root
    if (!spans.some(span => span.parentSpanId === null) && end - start < TRACE_LOOKUP_MAX_MS) {
        spans = await fetchSpans(organizationId, traceId, end - TRACE_LOOKUP_MAX_MS, end)
    }
    return orderForWaterfall(spans)
}

async function fetchSpans(organizationId: string | null, traceId: string, start: number, end: number): Promise<TraceSpan[]> {
    const body = await Kinotic.telemetry.findTrace(organizationId, traceId, start, end)
    return collectSpans(body.batches ?? [])
}

function collectSpans(batches: OtlpResourceSpans[]): TraceSpan[] {
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
                    error: span.status?.code === OtlpStatusCode.ERROR || span.status?.code === 'STATUS_CODE_ERROR',
                    statusMessage: span.status?.message ?? '',
                    attributes: attributesOf(span.attributes),
                    resource,
                    events: (span.events ?? []).map(eventOf),
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

function eventOf(event: OtlpSpanEvent): TraceSpanEvent {
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
function kindOf(kind: OtlpSpanType | OtlpSpanTypeName | undefined): string {
    let ret: string
    if (typeof kind === 'number') {
        ret = OtlpSpanType[kind]?.toLowerCase() ?? 'unspecified'
    } else if (typeof kind === 'string') {
        ret = kind.replace(/^SPAN_KIND_/, '').toLowerCase()
    } else {
        ret = 'unspecified'
    }
    return ret
}

// OTLP attributes are a list of {key, value: {stringValue | intValue | ...}} pairs
function attributesOf(list: OtlpKeyValue[] | undefined): Record<string, string> {
    const ret: Record<string, string> = {}
    for (const entry of list ?? []) {
        if (entry?.key !== undefined) {
            ret[entry.key] = anyValueToString(entry.value)
        }
    }
    return ret
}

// Lists and maps nested deeper than this are cut off, so an attribute cannot drive the conversion arbitrarily deep
const ANY_VALUE_MAX_DEPTH = 32
const ANY_VALUE_DEPTH_MARKER = '…'

/** An OTLP AnyValue as the native value it encodes: scalars as they are, lists and maps converted recursively. */
function anyValueToJson(value: OtlpAnyValue | undefined, depth: number = 0): unknown {
    let ret: unknown
    if (value === null || value === undefined) {
        ret = null
    } else if (value.stringValue !== undefined) {
        ret = String(value.stringValue)
    } else if (value.intValue !== undefined) {
        // an int64 arrives as a number or, past the safe range, as its decimal string
        ret = value.intValue
    } else if (value.doubleValue !== undefined) {
        ret = value.doubleValue
    } else if (value.boolValue !== undefined) {
        ret = value.boolValue
    } else if (value.bytesValue !== undefined) {
        ret = value.bytesValue
    } else if (depth >= ANY_VALUE_MAX_DEPTH) {
        ret = ANY_VALUE_DEPTH_MARKER
    } else if (value.arrayValue !== undefined) {
        ret = (value.arrayValue.values ?? []).map(entry => anyValueToJson(entry, depth + 1))
    } else if (value.kvlistValue !== undefined) {
        const map: Record<string, unknown> = {}
        for (const entry of value.kvlistValue.values ?? []) {
            if (entry?.key !== undefined) {
                map[entry.key] = anyValueToJson(entry.value, depth + 1)
            }
        }
        ret = map
    } else {
        ret = value
    }
    return ret
}

// A list or map is serialised once, from the native value, so a nested value is never stringified twice
function anyValueToString(value: OtlpAnyValue | undefined): string {
    const json = anyValueToJson(value)
    let ret: string
    if (json === null) {
        ret = ''
    } else if (typeof json === 'string') {
        ret = json
    } else {
        ret = JSON.stringify(json)
    }
    return ret
}

/** Evaluates a PromQL expression over the range, one series per result. */
export async function queryMetrics(organizationId: string | null, query: string, range: TimeRange): Promise<MetricSeries[]> {
    const body = await Kinotic.telemetry.queryMetrics({ organizationId, query, start: range.start, end: range.end, step: stepSeconds(range) })
    if (body.status !== 'success') {
        throw new Error(body.error ?? 'Metric query failed')
    }
    return (body.data?.result ?? []).map(result => ({
        name: seriesName(result.metric),
        points: (result.values ?? []).map(([seconds, value]) => [seconds * 1000, Number(value)] as [number, number])
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
