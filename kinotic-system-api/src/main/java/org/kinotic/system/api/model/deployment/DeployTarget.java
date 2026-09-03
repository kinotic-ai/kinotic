package org.kinotic.system.api.model.deployment;

/**
 * Where one deployment run puts the project: the node holding the checkout directory, the
 * absolute host directory of the checkout, and the id the run's sync workload is deployed
 * under, decided before it exists so its logs can be followed from the moment it starts.
 */
public record DeployTarget(String nodeId, String hostDir, String syncWorkloadId) {
}
