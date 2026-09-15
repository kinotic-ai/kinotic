/**
 * Parameters for a metric query: a PromQL expression evaluated over one organization's metrics
 * at a fixed step across a time range.
 */
export interface MetricQuery {

    /**
     * Organization whose metrics to query. Null names the platform's own, which only a system
     * participant may read.
     */
    organizationId: string | null

    /** The PromQL expression to evaluate, e.g. `sum by (service) (rate(traces_spanmetrics_calls_total[5m]))`. */
    query: string

    /** Start of the time range, epoch milliseconds (inclusive). */
    start: number

    /** End of the time range, epoch milliseconds (inclusive). */
    end: number

    /** Resolution of the result, in seconds between evaluated points. */
    step: number
}
