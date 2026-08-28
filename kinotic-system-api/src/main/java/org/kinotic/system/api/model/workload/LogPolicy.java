package org.kinotic.system.api.model.workload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * How much of a {@link Workload}'s log output is kept on the node. The workload's own image
 * and entrypoint have no say in it: the policy is applied by the VM provider, which owns the
 * log files, so a workload cannot fill the node by writing to stdout.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class LogPolicy {

    /**
     * Size at which the workload's current log file is rotated.
     */
    private int maxSizeMb = 10;

    /**
     * How many rotated files are kept alongside the current one. The oldest is discarded once
     * this many exist, so a workload's logs occupy at most maxSizeMb * (maxFiles + 1).
     */
    private int maxFiles = 3;
}
