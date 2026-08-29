/**
 * How much of a workload's log output is kept on the node. The workload's own image and
 * entrypoint have no say in it: the policy is applied by the VM provider, which owns the
 * log files, so a workload cannot fill the node by writing to stdout.
 */
export class LogPolicy {

    /**
     * Size at which the workload's current log file is rotated.
     */
    public maxSizeMb: number = 10

    /**
     * How many rotated files are kept alongside the current one. The oldest is discarded
     * once this many exist, so a workload's logs occupy at most maxSizeMb * (maxFiles + 1).
     */
    public maxFiles: number = 3

}
