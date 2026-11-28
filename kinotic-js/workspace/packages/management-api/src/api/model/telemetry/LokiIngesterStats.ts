import type { LokiStoreStats } from '@/api/model/telemetry/LokiStoreStats'

/** What the Loki ingesters read to answer a query. */
export interface LokiIngesterStats {

    /** How many ingesters the query reached. */
    totalReached?: number

    totalChunksMatched?: number

    /** How many batches the ingesters sent. */
    totalBatches?: number

    totalLinesSent?: number

    /** What the ingesters read from the chunks they hold. */
    store?: LokiStoreStats
}
