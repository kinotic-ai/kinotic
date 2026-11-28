import type { OtlpInstrumentationScope } from '@/api/model/telemetry/otlp/OtlpInstrumentationScope'
import type { OtlpSpan } from '@/api/model/telemetry/otlp/OtlpSpan'

/** The spans one instrumentation scope recorded. */
export interface OtlpScopeSpans {

    /** The library that recorded the spans. */
    scope?: OtlpInstrumentationScope

    spans?: OtlpSpan[]

    /** The schema the spans and their events follow. */
    schemaUrl?: string
}
