import type { OtlpKeyValue } from '@/api/model/telemetry/otlp/OtlpKeyValue'

/** The instrumentation library that recorded telemetry, such as `io.opentelemetry.elasticsearch-api`. */
export interface OtlpInstrumentationScope {

    /** The library's name; empty when unknown. */
    name?: string

    version?: string

    attributes?: OtlpKeyValue[]

    /** How many attributes were discarded for exceeding a limit. */
    droppedAttributesCount?: number
}
