package org.kinotic.system.api.model.workload;

/**
 * Whether a {@link VmNode} is taking workloads, as the node reports it. A node the platform cannot
 * reach is not a phase the node can report: it carries the condition
 * {@link org.kinotic.domain.api.model.StatusConditionType#NODE_UNREACHABLE} beside what it last
 * reported.
 */
public enum VmNodeStatusType {
    ONLINE,
    /**
     * The node reported something it can no longer guarantee: it keeps the workloads it has and takes no
     * more until it reports no problems.
     */
    DRAINING
}
