/**
 * Parameters for a trace search: the traces of one organization's workloads matching a TraceQL
 * query over a time range.
 */
export interface TraceQuery {

    /**
     * Organization whose traces to search. Null searches the caller's own organization, or the
     * platform's traces for a system participant.
     */
    organizationId: string | null

    /** The TraceQL query selecting the traces to return, e.g. `{ resource.application_id = "orders" }`. */
    query: string

    /** Start of the time range, epoch milliseconds (inclusive). */
    start: number

    /** End of the time range, epoch milliseconds (inclusive). */
    end: number

    /** Maximum number of traces to return. */
    limit: number
}
