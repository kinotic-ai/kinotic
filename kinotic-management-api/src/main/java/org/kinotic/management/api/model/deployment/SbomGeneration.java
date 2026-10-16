package org.kinotic.management.api.model.deployment;

import org.kinotic.management.api.model.TaskViews;

/**
 * A deployment run's SBOM step, as the {@link TaskViews#SBOM_GENERATION} view: the workload that
 * generates the project's SBOM, and whether the run generated it.
 *
 * @param workloadId the id of the workload that generates the SBOM
 * @param generated  true when the run generated the SBOM, false when the dependencies were
 *                   unchanged, null while the step runs
 */
public record SbomGeneration(String workloadId, Boolean generated) {
}
