/**
 * Whether a node is taking workloads, as the node reports it. A node the platform cannot reach is
 * not a phase the node can report: it carries the condition NODE_UNREACHABLE beside what it last
 * reported.
 */
export enum VmNodeStatusType {
    ONLINE = 'ONLINE',
    /**
     * The node reported something it can no longer guarantee: it keeps the workloads it has and takes
     * no more until it reports no problems.
     */
    DRAINING = 'DRAINING'
}
