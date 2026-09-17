/** One trace as Tempo's search lists it: its root span, and how long the whole trace took. */
export interface TraceSummary {
    traceId: string
    rootService: string
    rootName: string
    /** When the trace started, epoch milliseconds. */
    startMs: number
    durationMs: number
    /** How many of the trace's spans matched the search. */
    matchedSpans: number
}
