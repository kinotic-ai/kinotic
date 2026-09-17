import type { OtlpSignal } from '@/internal/api/model/OtlpSignal'

/** Where the node ships one OTLP signal, and the transform block that stamps it on the way. */
export interface OtlpSink {
    signal: OtlpSignal
    /** The block of otelcol.processor.transform that holds this signal's statements. */
    statements: string
    /** Base URL of the OTLP/HTTP endpoint the signal is exported to. */
    url: string
}
