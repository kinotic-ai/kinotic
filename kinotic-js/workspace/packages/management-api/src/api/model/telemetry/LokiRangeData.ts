import type { LokiQueryStats } from '@/api/model/telemetry/LokiQueryStats'
import type { LokiStream } from '@/api/model/telemetry/LokiStream'

/** The result of a Loki range query for log lines. */
export interface LokiRangeData {

    /** The kind of result, which for a log query is `streams`. */
    resultType: 'streams'

    /** One stream per label set, each with its lines. */
    result: LokiStream[]

    /** What answering the query read and how long it took. */
    stats?: LokiQueryStats
}
