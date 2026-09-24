import type { TrafficSignal } from '@/api/model/telemetry/TrafficSignal'

/**
 * Parameters for a traffic query: one signal of the invocations clients made through the gateway on
 * behalf of an organization or one of its applications, at a fixed step across a time range.
 */
export interface TrafficQuery {

    /**
     * Organization whose callers' invocations to read, summed over all its applications. Null reads
     * every invocation on the platform, which only a system participant may do.
     */
    organizationId: string | null

    /**
     * Narrows the organization's invocations to those of one of its applications' callers; null
     * reads them all. Requires an organization.
     */
    applicationId: string | null

    /** The signal to evaluate. */
    signal: TrafficSignal

    /** Start of the time range, epoch milliseconds (inclusive). */
    start: number

    /** End of the time range, epoch milliseconds (inclusive). */
    end: number

    /** Resolution of the result, in seconds between evaluated points. */
    step: number
}
