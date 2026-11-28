/** The totals of a Loki query. Times are seconds. */
export interface LokiSummaryStats {

    bytesProcessedPerSecond?: number

    linesProcessedPerSecond?: number

    totalBytesProcessed?: number

    totalLinesProcessed?: number

    /** How long the query ran. */
    execTime?: number

    /** How long the query waited in the queue before it ran. */
    queueTime?: number
}
