package org.kinotic.management.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Parameters for a traffic query: one {@link TrafficSignal} of the invocations clients made through
 * the gateway on behalf of an organization or one of its applications, at a fixed step across a
 * time range.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class TrafficQuery {

    /**
     * Organization whose callers' invocations to read, summed over all its applications. Null reads
     * every invocation on the platform, which only a system participant may do.
     */
    private String organizationId;

    /**
     * Narrows the organization's invocations to those of one of its applications' callers; null
     * reads them all. Requires an organization.
     */
    private String applicationId;

    /**
     * The signal to evaluate.
     */
    private TrafficSignal signal;

    /**
     * Start of the time range, epoch milliseconds (inclusive).
     */
    private long start;

    /**
     * End of the time range, epoch milliseconds (inclusive).
     */
    private long end;

    /**
     * Resolution of the result, in seconds between evaluated points.
     */
    private long step;
}
