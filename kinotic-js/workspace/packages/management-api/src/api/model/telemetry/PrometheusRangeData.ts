import type { PrometheusSeries } from '@/api/model/telemetry/PrometheusSeries'

/** The result of a Prometheus range query. */
export interface PrometheusRangeData {

    /** The kind of result; a range query yields `matrix`. */
    resultType: 'matrix' | 'vector' | 'scalar' | 'string'

    /** One series per label set the expression produced, sorted by their labels. */
    result: PrometheusSeries[]
}
