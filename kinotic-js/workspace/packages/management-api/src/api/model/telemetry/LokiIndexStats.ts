/** What a Loki query read from the index. */
export interface LokiIndexStats {

    totalChunks?: number

    /** The chunks left after filtering. */
    postFilterChunks?: number

    /** The time spent fetching shards, nanoseconds. */
    shardsDuration?: number

    /** Whether bloom filters were used to filter chunks. */
    usedBloomFilters?: boolean
}
