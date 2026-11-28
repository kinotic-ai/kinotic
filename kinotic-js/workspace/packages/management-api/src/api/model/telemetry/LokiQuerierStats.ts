import type { LokiStoreStats } from '@/api/model/telemetry/LokiStoreStats'

/** What the Loki queriers read to answer a query. */
export interface LokiQuerierStats {

    store?: LokiStoreStats
}
