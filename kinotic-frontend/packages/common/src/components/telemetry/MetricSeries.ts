/** One time series a metric query returned: its label and its samples. */
export interface MetricSeries {
    name: string
    /** The labels that set the series apart, the metric name aside. */
    labels: Record<string, string>
    /** [epoch milliseconds, value] pairs in time order. */
    points: Array<[number, number]>
}
