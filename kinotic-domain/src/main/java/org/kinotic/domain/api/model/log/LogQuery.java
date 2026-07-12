package org.kinotic.domain.api.model.log;

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
