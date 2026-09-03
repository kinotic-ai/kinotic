package org.kinotic.management.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Parameters for a trace search: the traces of one organization's workloads matching a TraceQL
 * query over a time range.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class TraceQuery {

    /**
     * Organization whose traces to search. Null searches the caller's own organization, or the
     * platform's traces for a system participant.
     */
    private String organizationId;

    /**
     * The TraceQL query selecting the traces to return, e.g.
     * {@code { resource.application_id = "orders" && status = error }}.
     */
    private String query;

    /**
     * Start of the time range, epoch milliseconds (inclusive).
     */
    private long start;

    /**
     * End of the time range, epoch milliseconds (inclusive).
     */
    private long end;

    /**
     * Maximum number of traces to return.
     */
    private int limit;
}
