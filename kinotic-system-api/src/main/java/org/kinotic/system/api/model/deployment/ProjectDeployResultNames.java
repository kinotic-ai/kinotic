package org.kinotic.system.api.model.deployment;

import org.kinotic.management.api.model.deployment.DeployTarget;
import org.kinotic.management.api.model.deployment.ProjectArtifacts;

/**
 * The names a project deployment run's tasks store their durable results under in the job scope:
 * the values later tasks inject, which a resumed run replays instead of running their tasks again.
 */
public final class ProjectDeployResultNames {

    /** The resolved {@link DeployTarget}: the node, the checkout directory and the run's workload ids. */
    public static final String DEPLOY_TARGET = "deployTarget";

    /**
     * The {@link ProjectArtifacts} of the deployed commit, as the sync workload found them in the
     * checkout and the run bound them.
     */
    public static final String ARTIFACTS = "artifacts";

    private ProjectDeployResultNames() {
    }
}
