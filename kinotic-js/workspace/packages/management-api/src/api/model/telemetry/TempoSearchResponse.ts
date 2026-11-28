import type { TempoSearchMetrics } from '@/api/model/telemetry/TempoSearchMetrics'
import type { TempoSearchTrace } from '@/api/model/telemetry/TempoSearchTrace'

/**
 * Tempo's answer to a trace search: the traces it found, up to the search's limit, and what the
 * search read. A field left at its zero value is absent.
 */
export interface TempoSearchResponse {

    /** The traces found; absent when none were. */
    traces?: TempoSearchTrace[]

    /** How much the search read to answer. */
    metrics?: TempoSearchMetrics
}
