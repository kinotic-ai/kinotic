package org.kinotic.system.api.model.workload;

/**
 * The reconciled state of a {@link VmNode}: the shape both the intent and the observation take, so a
 * node is in its desired state exactly when the two are equal. Intent is that the node takes
 * workloads; observation is what the node reports it can do.
 *
 * @param phase whether the node takes workloads
 */
public record VmNodeState(VmNodeStatusType phase) {
}
