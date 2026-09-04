import type { TraceSpanEvent } from './TraceSpanEvent'

/** One span of a trace, flattened for the waterfall: where it sits in the tree and what it recorded. */
export interface TraceSpan {
    spanId: string
    parentSpanId: string | null
    name: string
    /** The service.name of the resource that emitted the span. */
    service: string
    /** The span kind in lower case: server, client, producer, consumer, or internal. */
    kind: string
    /** When the span started, epoch milliseconds. */
    startMs: number
    durationMs: number
    error: boolean
    statusMessage: string
    attributes: Record<string, string>
    resource: Record<string, string>
    events: TraceSpanEvent[]
    /** Nesting depth under the trace's root, which the waterfall indents by. */
    depth: number
}
