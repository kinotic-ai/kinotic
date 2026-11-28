/** What a Loki query read from the store. Times are seconds. */
export interface LokiStoreStats {

    compressedBytes?: number

    decompressedBytes?: number

    decompressedLines?: number

    chunksDownloadTime?: number

    totalChunksRef?: number

    totalChunksDownloaded?: number

    totalDuplicates?: number
}
