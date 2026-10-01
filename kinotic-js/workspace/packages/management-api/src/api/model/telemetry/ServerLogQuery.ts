/**
 * Parameters for a historical log query: one platform server's logs, or one node's of it, over a time range.
 */
export interface ServerLogQuery {

    /** The service name that labels the server's logs, as its cluster nodes report it. */
    telemetryServiceName: string

    /** The service instance id that labels one node's logs, as that cluster node reports it; null for every node of the server. */
    telemetryServiceInstanceId: string | null

    /** Start of the time range, epoch milliseconds (inclusive). */
    start: number

    /** End of the time range, epoch milliseconds (inclusive). */
    end: number

    /** Maximum number of log entries to return. */
    limit: number
}
