/** The totals of a Loki query. */
export interface LokiSummaryStats {

    bytesProcessedPerSecond?: number

    linesProcessedPerSecond?: number

    /** The bytes processed, structured metadata included. */
    totalBytesProcessed?: number

    totalLinesProcessed?: number

    /** How long the query ran, seconds. */
    execTime?: number

    /** How long the query waited in the queue before it ran, seconds. */
    queueTime?: number

    /** Kept by Loki for older clients; {@link splits} and {@link shards} replace it. */
    subqueries?: number

    /** How many result entries the query returned. */
    totalEntriesReturned?: number

    /** How many pieces the query was split into by time. */
    splits?: number

    /** How many shards the query was split into. */
    shards?: number

    /** The lines left after the query's filters. */
    totalPostFilterLines?: number

    totalStructuredMetadataBytesProcessed?: number
}
