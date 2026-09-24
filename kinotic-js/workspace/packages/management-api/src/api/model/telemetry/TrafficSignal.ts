/**
 * A signal of the invocations clients made through the gateway, evaluated as a series over time.
 */
export enum TrafficSignal {
    /** Invocations per second. */
    REQUESTS = 'REQUESTS',
    /** The share of invocations that failed: an error reply, or no service to take them. */
    ERRORS = 'ERRORS',
    /** The 95th percentile time to first reply, in seconds. */
    LATENCY_P95 = 'LATENCY_P95'
}
