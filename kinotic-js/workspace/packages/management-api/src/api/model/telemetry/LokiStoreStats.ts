import type { LokiChunkStats } from '@/api/model/telemetry/LokiChunkStats'

/** What a Loki query read from a chunk store. */
export interface LokiStoreStats {

    /** How many chunk references the query fetched from the index. */
    totalChunksRef?: number

    totalChunksDownloaded?: number

    /** The time spent fetching chunks, nanoseconds. */
    chunksDownloadTime?: number

    /** Whether the query referenced structured metadata. */
    queryReferencedStructuredMetadata?: boolean

    /** What the query read from the chunks themselves. */
    chunk?: LokiChunkStats

    /** The time spent fetching chunk references from the index. */
    chunkRefsFetchTime?: number

    /** The time spent blocked on congestion control. */
    congestionControlLatency?: number

    /** How many lines the pipeline wrapper filtered out. */
    pipelineWrapperFilteredLines?: number
}
