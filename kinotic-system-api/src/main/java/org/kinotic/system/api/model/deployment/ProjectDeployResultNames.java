package org.kinotic.system.api.model.deployment;

import org.kinotic.management.api.model.deployment.DeployTarget;

/**
 * The names a project deployment run's tasks store their results under in the job scope. A
 * resumed run replays a stored result instead of running its task again, and each result reaches
 * a watcher of the run on its {@code TaskCompletedEvent}s and outlives the run on its
 * {@code TaskRecord}s, which is how the console shows what each task produced and finds the
 * workloads whose logs belong to a run.
 */
public final class ProjectDeployResultNames {

    /** The resolved {@link DeployTarget}, including the id of the run's sync workload. */
    public static final String DEPLOY_TARGET = "deployTarget";

    /**
     * The {@link org.kinotic.management.api.model.deployment.ProjectArtifacts} of the deployed commit,
     * as the sync workload found them in the checkout and the run bound them.
     */
    public static final String ARTIFACTS = "artifacts";

    /**
     * The id of the run's sync workload, stored by the task that ran it. Known before that
     * task completes through {@link DeployTarget#syncWorkloadId()}, which is what lets a
     * watcher follow the workload's logs while the task runs.
     */
    public static final String SYNC_WORKLOAD_ID = "syncWorkloadId";

    /**
     * The {@link MicroserviceDeployments} the run left the project with, one per microservice
     * of the project, ensured or orphaned.
     */
    public static final String MICROSERVICE_DEPLOYMENTS = "microserviceDeployments";

    /**
     * The {@link UiDeployments} the run left the project with, one per UI of the project,
     * published or orphaned. Known before the task completes through
     * {@link DeployTarget#uiPublishWorkloadId()}, which is what lets a watcher follow the
     * publish workload's logs while the task runs.
     */
    public static final String UI_DEPLOYMENTS = "uiDeployments";

    /**
     * Whether the run generated the project's SBOM: {@code true} when its SBOM workload did,
     * {@code false} when the SBOM already listed the dependencies the sync reported. The workload is
     * known before the task completes through {@link DeployTarget#sbomWorkloadId()}, which is what
     * lets a watcher follow its logs while the task runs.
     */
    public static final String SBOM = "sbom";

    private ProjectDeployResultNames() {
    }
}
