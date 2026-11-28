import type { OtlpKeyValue } from '@/api/model/telemetry/otlp/OtlpKeyValue'

/** A link from an OTLP span to another span, in the same trace or another. */
export interface OtlpSpanLink {

    traceId?: string

    spanId?: string

    /** The W3C tracestate of the linked span. */
    traceState?: string

    attributes?: OtlpKeyValue[]

    droppedAttributesCount?: number

    /** The linked span's W3C trace flags in bits 0–7, and in bits 8 and 9 whether and that it was remote. */
    flags?: number
}
