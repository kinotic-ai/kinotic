package org.kinotic.management.api.model.deployment;

/**
 * Where one deployment run puts the project: the node holding the checkout directory, the
 * absolute host directory of the checkout, and the ids the run's sync, UI publish and SBOM
 * workloads are deployed under, decided before they exist so their logs can be followed
 * from the moment they start.
 */
public record DeployTarget(String nodeId, String hostDir, String syncWorkloadId, String uiPublishWorkloadId,
                           String sbomWorkloadId) {
}
