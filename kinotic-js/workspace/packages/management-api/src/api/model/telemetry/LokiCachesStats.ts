import type { LokiCacheStats } from '@/api/model/telemetry/LokiCacheStats'

/** How a Loki query used each of Loki's caches. */
export interface LokiCachesStats {

    chunk?: LokiCacheStats

    index?: LokiCacheStats

    result?: LokiCacheStats

    statsResult?: LokiCacheStats

    volumeResult?: LokiCacheStats

    seriesResult?: LokiCacheStats

    labelResult?: LokiCacheStats

    instantMetricResult?: LokiCacheStats
}
