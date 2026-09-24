package org.kinotic.management.api.model;

/**
 * A signal of the invocations clients made through the gateway, evaluated as a series over time.
 */
public enum TrafficSignal {

    /** Invocations per second. */
    REQUESTS,

    /** The share of invocations that failed: an error reply, or no service to take them. */
    ERRORS,

    /** The 95th percentile time to first reply, in seconds. */
    LATENCY_P95
}
