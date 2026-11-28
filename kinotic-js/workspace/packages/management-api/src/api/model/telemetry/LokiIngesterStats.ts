/** What the Loki ingesters read to answer a query. */
export interface LokiIngesterStats {

    compressedBytes?: number

    decompressedBytes?: number

    decompressedLines?: number

    /** The bytes and lines read from chunks still open in memory. */
    headChunkBytes?: number

    headChunkLines?: number

    totalBatches?: number

    totalChunksMatched?: number

    totalDuplicates?: number

    totalLinesSent?: number

    /** How many ingesters answered. */
    totalReached?: number
}
