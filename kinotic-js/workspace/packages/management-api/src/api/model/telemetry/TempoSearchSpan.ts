import type { OtlpKeyValue } from '@/api/model/telemetry/otlp/OtlpKeyValue'

/** One span a Tempo search matched, with the attributes the query selected. */
export interface TempoSearchSpan {

    /** The span id, in hex. */
    spanID: string

    name?: string

    /** When the span started, epoch nanoseconds as a decimal string. */
    startTimeUnixNano?: string

    /** How long the span took, nanoseconds as a decimal string. */
    durationNanos?: string

    /** The span's attributes the query named or selected. */
    attributes?: OtlpKeyValue[]
}
