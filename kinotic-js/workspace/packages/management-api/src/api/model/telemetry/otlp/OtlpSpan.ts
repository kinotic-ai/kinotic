import type { OtlpKeyValue } from '@/api/model/telemetry/otlp/OtlpKeyValue'
import type { OtlpSpanEvent } from '@/api/model/telemetry/otlp/OtlpSpanEvent'
import type { OtlpSpanLink } from '@/api/model/telemetry/otlp/OtlpSpanLink'
import type { OtlpStatus } from '@/api/model/telemetry/otlp/OtlpStatus'

/**
 * One span in OTLP JSON, as Tempo returns it. A field left at its default value is absent. Ids are
 * base64 of their bytes or hex, and a 64-bit time is its decimal string.
 */
export interface OtlpSpan {

    traceId?: string

    spanId: string

    /** The W3C tracestate the span carried. */
    traceState?: string

    /** The parent span's id; absent for a root span. */
    parentSpanId?: string

    /** The W3C trace flags in bits 0–7, and in bits 8 and 9 whether and that the parent was remote. */
    flags?: number

    name?: string

    /**
     * The span's kind, as its number or its name: 0 or `SPAN_KIND_UNSPECIFIED`, 1 `SPAN_KIND_INTERNAL`,
     * 2 `SPAN_KIND_SERVER`, 3 `SPAN_KIND_CLIENT`, 4 `SPAN_KIND_PRODUCER`, 5 `SPAN_KIND_CONSUMER`.
     */
    kind?: number | string

    /** When the span started, epoch nanoseconds. */
    startTimeUnixNano?: string

    /** When the span ended, epoch nanoseconds. */
    endTimeUnixNano?: string

    attributes?: OtlpKeyValue[]

    droppedAttributesCount?: number

    /** The events recorded on the span, such as exceptions. */
    events?: OtlpSpanEvent[]

    droppedEventsCount?: number

    /** The spans this span is linked to, in this trace or another. */
    links?: OtlpSpanLink[]

    droppedLinksCount?: number

    /** The span's status; absent when unset. */
    status?: OtlpStatus
}
