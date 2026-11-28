import type { LokiRangeData } from '@/api/model/telemetry/LokiRangeData'

/** The answer to a Loki query_range request for log lines. */
export interface LokiRangeResponse {

    /** Whether the query succeeded. */
    status: string

    /** The log streams the query read, and what reading them took. */
    data?: LokiRangeData
}
