import type { LokiIngesterStats } from '@/api/model/telemetry/LokiIngesterStats'
import type { LokiStoreStats } from '@/api/model/telemetry/LokiStoreStats'
import type { LokiSummaryStats } from '@/api/model/telemetry/LokiSummaryStats'

/**
 * What answering a Loki query read and how long it took, as Loki documents it. A Loki version may
 * report sections beyond these, which are kept under their own keys.
 */
export interface LokiQueryStats {

    /** The totals of the whole query. */
    summary?: LokiSummaryStats

    /** What the ingesters read, from data not yet flushed to the store. */
    ingester?: LokiIngesterStats

    /** What was read from the store. */
    store?: LokiStoreStats

    [section: string]: unknown
}
