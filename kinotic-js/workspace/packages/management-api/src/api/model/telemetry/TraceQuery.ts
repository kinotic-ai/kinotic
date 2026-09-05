/**
 * Parameters for a trace search: the traces of one organization's workloads matching a TraceQL
 * query over a time range.
 */
export interface TraceQuery {

    /**
     * Organization whose traces to search. Null names the platform's own, which only a system
     * participant may read.
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
