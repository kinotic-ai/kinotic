/** One time series a metric query returned: its label and its samples. */
export interface MetricSeries {
    name: string
    /** [epoch milliseconds, value] pairs in time order. */
    points: Array<[number, number]>
}
