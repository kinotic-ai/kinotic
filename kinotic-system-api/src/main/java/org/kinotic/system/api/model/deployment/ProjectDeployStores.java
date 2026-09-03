package org.kinotic.system.api.model.deployment;

/**
 * The job scope names a project deployment run stores its outcomes under. They reach a
 * watcher of the run on its {@code TaskCompletedEvent}s and outlive it on its
 * {@code TaskRecord}s, which is how the console finds the workloads whose logs belong to a
 * run.
 */
public final class ProjectDeployStores {

    /** The resolved {@link DeployTarget}, including the id of the run's sync workload. */
    public static final String DEPLOY_TARGET = "deployTarget";

    /**
     * The {@link org.kinotic.management.api.model.ProjectArtifacts} of the deployed commit,
     * as the sync workload found them in the checkout and the run bound them.
     */
    public static final String ARTIFACTS = "artifacts";

    /**
     * The id of the run's sync workload, stored by the task that ran it. Known before that
     * task completes through {@link DeployTarget#syncWorkloadId()}, which is what lets a
     * watcher follow the workload's logs while the task runs.
     */
    public static final String SYNC_WORKLOAD_ID = "syncWorkloadId";

    /** The id of the runtime workload serving the deployment. */
    public static final String RUNTIME_WORKLOAD_ID = "runtimeWorkloadId";

    private ProjectDeployStores() {
    }
}
