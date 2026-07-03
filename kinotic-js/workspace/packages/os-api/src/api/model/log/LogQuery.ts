/**
 * Parameters for a historical log query against Loki's query_range endpoint.
 */
export interface LogQuery {

    /** LogQL query selecting the log streams to return. */
    query: string

    /** Start of the time range, epoch milliseconds (inclusive). */
    start: number

    /** End of the time range, epoch milliseconds (inclusive). */
    end: number

    /** Maximum number of log entries to return. */
    limit: number
}
