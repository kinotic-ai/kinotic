package org.kinotic.domain.api.model.log;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Parameters for a historical log query against Loki's {@code query_range} endpoint.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class LogQuery {

    /**
     * LogQL query selecting the log streams to return.
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
     * Maximum number of log entries to return.
     */
    private int limit;
}
