package org.kinotic.management.api.model.telemetry;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Parameters for a historical log query: one platform server's logs, or one node's of it, over a time range.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ServerLogQuery {

    /**
     * The service name that labels the server's logs, as its cluster nodes report it.
     */
    private String telemetryServiceName;

    /**
     * The service instance id that labels one node's logs, as that cluster node reports it; null for every node of
     * the server.
     */
    private String telemetryServiceInstanceId;

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
