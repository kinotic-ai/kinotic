package org.kinotic.orchestrator.api.model.workload;

/**
 * Whether a {@link VmNode} is taking workloads.
 */
public enum VmNodeStatusType {
    ONLINE,
    OFFLINE,
    DRAINING
}
