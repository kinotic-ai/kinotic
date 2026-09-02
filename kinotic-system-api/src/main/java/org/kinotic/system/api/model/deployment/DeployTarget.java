package org.kinotic.system.api.model.deployment;

/**
 * Where one deployment run puts the project: the node holding the checkout directory, the
 * absolute host directory of the checkout, the runtime workload already serving it —
 * {@code null} when this run must create it (first deployment of the project) — and the id
 * the run's sync workload is deployed under, decided before it exists so its logs can be
 * followed from the moment it starts.
 */
public record DeployTarget(String nodeId, String hostDir, String runtimeWorkloadId, String syncWorkloadId) {
}
