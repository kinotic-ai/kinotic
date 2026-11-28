import type { TempoServiceStats } from '@/api/model/telemetry/TempoServiceStats'
import type { TempoSpanSet } from '@/api/model/telemetry/TempoSpanSet'

/** One trace a Tempo search found, summarized by its root span. */
export interface TempoSearchTrace {

    /** The trace id, in hex. */
    traceID: string

    /**
     * The service of the trace's root span, or `<root span not yet received>` when Tempo holds no
     * span of the trace without a parent.
     */
    rootServiceName?: string

    /** The name of the trace's root span; absent when Tempo holds no root span. */
    rootTraceName?: string

    /** When the trace started, epoch nanoseconds as a decimal string. */
    startTimeUnixNano?: string

    /** How long the trace took, milliseconds; absent when under one millisecond. */
    durationMs?: number

    /** The first of {@link spanSets}, which Tempo still fills for older clients. */
    spanSet?: TempoSpanSet

    /** The spans of the trace that matched the search, in sets. */
    spanSets?: TempoSpanSet[]

    /** The spans and failed spans of the trace, by the service that emitted them. */
    serviceStats?: Record<string, TempoServiceStats>
}
