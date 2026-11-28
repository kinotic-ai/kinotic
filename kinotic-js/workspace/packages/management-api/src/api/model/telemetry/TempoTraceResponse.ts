import type { OtlpResourceSpans } from '@/api/model/telemetry/otlp/OtlpResourceSpans'

/** Tempo's answer to a trace lookup: the trace's spans as OTLP JSON. */
export interface TempoTraceResponse {

    /** The trace's spans grouped by the resource that emitted them, OTLP's resourceSpans. */
    batches?: OtlpResourceSpans[]
}
