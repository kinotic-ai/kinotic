/**
 * Parameters for a historical log query: one workload's logs over a time range.
 */
export interface LogQuery {

    /**
     * Organization whose workload's logs to return. Null names the platform's own, which only a
     * system participant may read.
     */
    organizationId: string | null

    /** Id of the workload whose logs to return. */
    workloadId: string

    /** Start of the time range, epoch milliseconds (inclusive). */
    start: number

    /** End of the time range, epoch milliseconds (inclusive). */
    end: number

    /** Maximum number of log entries to return. */
    limit: number
}
