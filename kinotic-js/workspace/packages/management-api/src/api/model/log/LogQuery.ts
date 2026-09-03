/**
 * Parameters for a historical log query: one workload's logs over a time range.
 */
export interface LogQuery {

    /** Id of the workload whose logs to return. */
    workloadId: string

    /** Start of the time range, epoch milliseconds (inclusive). */
    start: number

    /** End of the time range, epoch milliseconds (inclusive). */
    end: number

    /** Maximum number of log entries to return. */
    limit: number
}
