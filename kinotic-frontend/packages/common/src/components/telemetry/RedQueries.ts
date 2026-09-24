/** The PromQL of one telemetry view's rate, errors and duration. */
export interface RedQueries {
    requests: string
    errors: string
    latencyP95: string
}
