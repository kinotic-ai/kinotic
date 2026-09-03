package org.kinotic.management.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Parameters for a metric query: a PromQL expression evaluated over one organization's metrics
 * at a fixed step across a time range.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class MetricQuery {

    /**
     * Organization whose metrics to query. Null queries the caller's own organization, or the
     * platform's metrics for a system participant.
     */
    private String organizationId;

    /**
     * The PromQL expression to evaluate, e.g.
     * {@code sum by (service) (rate(traces_spanmetrics_calls_total[5m]))}.
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
     * Resolution of the result, in seconds between evaluated points.
     */
    private long step;
}
