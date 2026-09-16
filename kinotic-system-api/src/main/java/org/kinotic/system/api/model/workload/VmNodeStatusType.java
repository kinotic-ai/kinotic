package org.kinotic.system.api.model.workload;

/**
 * Whether a {@link VmNode} is taking workloads.
 */
public enum VmNodeStatusType {
    ONLINE,
    OFFLINE,
    DRAINING,
    /**
     * A call to the node's vm-manager could not be delivered and the vm-manager holds no registration: the
     * node takes no workloads until its next heartbeat brings it back, and its workloads are failed by the
     * heartbeat timeout like any other silent node's.
     */
    UNREACHABLE
}
