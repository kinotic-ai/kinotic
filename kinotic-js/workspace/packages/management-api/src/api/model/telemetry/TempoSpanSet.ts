import type { OtlpKeyValue } from '@/api/model/telemetry/otlp/OtlpKeyValue'
import type { TempoSearchSpan } from '@/api/model/telemetry/TempoSearchSpan'

/** The spans of one trace that matched a Tempo search. */
export interface TempoSpanSet {

    /** The matched spans Tempo returned, which may be fewer than {@link matched}. */
    spans?: TempoSearchSpan[]

    /** How many spans of the trace matched. */
    matched?: number

    /** The values of the query's aggregates and selected attributes for the set. */
    attributes?: OtlpKeyValue[]
}
