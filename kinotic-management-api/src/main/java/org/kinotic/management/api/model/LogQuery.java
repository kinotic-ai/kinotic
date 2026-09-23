package org.kinotic.management.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Parameters for a historical log query: one workload's logs over a time range.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class LogQuery {

    /**
     * Organization whose workload's logs to return. Null names the platform's own, which only a
     * system participant may read.
     */
    private String organizationId;

    /**
     * Id of the workload whose logs to return.
     */
    private String workloadId;

    /**
     * Start of the time range, epoch milliseconds (inclusive).
     */
    private long start;

    /**
     * End of the time range, epoch milliseconds (inclusive).
     */
    private long end;

    /**
     * Maximum number of log entries to return.
     */
    private int limit;
}
