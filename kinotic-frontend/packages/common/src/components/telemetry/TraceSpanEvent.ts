/** Something a span recorded at a point in its life: an exception, a message, a milestone. */
export interface TraceSpanEvent {
    name: string
    /** When it happened, epoch milliseconds. */
    timeMs: number
    attributes: Record<string, string>
}
