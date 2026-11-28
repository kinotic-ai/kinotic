import type { PrometheusRangeData } from '@/api/model/telemetry/PrometheusRangeData'

/** The answer to a Prometheus query_range request: the series the expression evaluated to, or why it failed. */
export interface PrometheusRangeResponse {

    /** Whether the query succeeded. */
    status: 'success' | 'error'

    /** The series; may also be present on an error. */
    data?: PrometheusRangeData

    /** The kind of failure, such as `bad_data`, `execution` or `timeout`, when the query failed. */
    errorType?: string

    /** Why the query failed, when it did. */
    error?: string

    /** Problems that did not stop the query from answering. */
    warnings?: string[]

    /** Informational notes about the query's evaluation. */
    infos?: string[]
}
