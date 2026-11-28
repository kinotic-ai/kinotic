/** A Prometheus native histogram at one point in time. */
export interface PrometheusHistogram {

    /** How many observations it holds, as a decimal string. */
    count: string

    /** The sum of its observations, as a decimal string. */
    sum: string

    /**
     * Its buckets, each [boundary rule, left boundary, right boundary, observations in the bucket].
     * The rule is 0 for open left, 1 open right, 2 open both and 3 closed both; the rest are decimal strings.
     */
    buckets?: [number, string, string, string][]
}
