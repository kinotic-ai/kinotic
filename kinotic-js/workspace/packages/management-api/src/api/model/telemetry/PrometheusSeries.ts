import type { PrometheusHistogram } from '@/api/model/telemetry/PrometheusHistogram'

/** One series of a Prometheus range query: its labels and its points. */
export interface PrometheusSeries {

    /** The series' labels, `__name__` among them when the series keeps its metric name. */
    metric: Record<string, string>

    /**
     * The series' float points, each its time in epoch seconds and its value as a decimal string,
     * which can also spell NaN and the infinities.
     */
    values?: [number, string][]

    /** The series' native histogram points, each its time in epoch seconds and the histogram. */
    histograms?: [number, PrometheusHistogram][]
}
