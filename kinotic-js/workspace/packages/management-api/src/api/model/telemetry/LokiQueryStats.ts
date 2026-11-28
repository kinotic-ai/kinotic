import type { LokiCachesStats } from '@/api/model/telemetry/LokiCachesStats'
import type { LokiIndexStats } from '@/api/model/telemetry/LokiIndexStats'
import type { LokiIngesterStats } from '@/api/model/telemetry/LokiIngesterStats'
import type { LokiQuerierStats } from '@/api/model/telemetry/LokiQuerierStats'
import type { LokiSummaryStats } from '@/api/model/telemetry/LokiSummaryStats'

/** What answering a Loki query read and how long it took. */
export interface LokiQueryStats {

    /** The totals of the whole query. */
    summary?: LokiSummaryStats

    /** What the queriers read from the store. */
    querier?: LokiQuerierStats

    /** What the ingesters read, from data they had yet to flush to the store. */
    ingester?: LokiIngesterStats

    /** How the query used each of Loki's caches. */
    cache?: LokiCachesStats

    /** What the query read from the index. */
    index?: LokiIndexStats
}
