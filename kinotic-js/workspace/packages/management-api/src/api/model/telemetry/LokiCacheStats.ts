/** How a Loki query used one cache. */
export interface LokiCacheStats {

    entriesFound?: number

    entriesRequested?: number

    entriesStored?: number

    bytesReceived?: number

    bytesSent?: number

    requests?: number

    /** The time spent reading from the cache. */
    downloadTime?: number

    /** How much of the query's time range the cache served. */
    queryLengthServed?: number
}
