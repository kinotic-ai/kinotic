package org.kinotic.system.internal.api.model.deployment;

/**
 * Where one deployment run puts the project: the node holding the checkout directory, the
 * absolute host directory of the checkout, and the runtime workload already serving it —
 * {@code null} when this run must create it (first deployment of the project).
 */
public record DeployTarget(String nodeId, String hostDir, String runtimeWorkloadId) {

}
