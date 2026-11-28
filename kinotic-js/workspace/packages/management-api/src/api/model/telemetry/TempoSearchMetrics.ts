/** How much a Tempo search read to answer. A 64-bit count arrives as its decimal string. */
export interface TempoSearchMetrics {

    inspectedTraces?: number

    inspectedBytes?: string

    inspectedSpans?: string

    /** The blocks the search covered. */
    totalBlocks?: number

    /** The block bytes the search covered. */
    totalBlockBytes?: string

    /** The jobs the search was split into, and how many of them finished. */
    totalJobs?: number

    completedJobs?: number
}
