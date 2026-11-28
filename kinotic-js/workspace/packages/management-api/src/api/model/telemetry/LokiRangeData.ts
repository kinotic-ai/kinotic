import type { LokiQueryStats } from '@/api/model/telemetry/LokiQueryStats'
import type { LokiStream } from '@/api/model/telemetry/LokiStream'

/** The result of a Loki range query for log lines. */
export interface LokiRangeData {

    /** The kind of result; a log query yields `streams`. */
    resultType: string

    /** One stream per label set, each with its lines. */
    result: LokiStream[]

    /** What answering the query read and how long it took. */
    stats?: LokiQueryStats
}
