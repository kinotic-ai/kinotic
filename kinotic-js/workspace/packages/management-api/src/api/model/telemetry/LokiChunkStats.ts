/** What a Loki query read from chunks. */
export interface LokiChunkStats {

    /** The bytes and lines read from chunks still open in memory. */
    headChunkBytes?: number

    headChunkLines?: number

    decompressedBytes?: number

    decompressedLines?: number

    /** The bytes of compressed chunks processed. */
    compressedBytes?: number

    /** How many duplicate entries were found while processing. */
    totalDuplicates?: number

    /** The lines left after filtering. */
    postFilterLines?: number

    /** The structured metadata bytes read from chunks still open in memory. */
    headChunkStructuredMetadataBytes?: number

    /** The structured metadata bytes decompressed from chunks. */
    decompressedStructuredMetadataBytes?: number
}
