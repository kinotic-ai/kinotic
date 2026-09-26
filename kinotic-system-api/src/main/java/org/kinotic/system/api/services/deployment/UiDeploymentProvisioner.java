package org.kinotic.system.api.services.deployment;

import io.vertx.core.Future;
import org.kinotic.management.api.model.deployment.DeploymentStatus;
import org.kinotic.management.api.model.deployment.DeploymentStatusType;
import org.kinotic.management.api.model.deployment.UiDeployment;

/**
 * Reports whether a published UI serves at its hostname. Every site is served through what the
 * platform provisions once, so nothing is created per site; a deployment is
 * {@link DeploymentStatusType#PROVISIONING} from a publish until its site serves the published
 * commit, which its worker keeps checking here.
 */
public interface UiDeploymentProvisioner {

    /**
     * Checks whether the site serves the given commit and its index at the deployment's hostname.
     *
     * @param deployment the deployment, persisted with its label as id and its files uploaded
     * @param commitSha  the commit the site should serve
     * @return a future emitting {@link DeploymentStatusType#READY} once the site serves the commit,
     *         and {@link DeploymentStatusType#PROVISIONING} with what was observed while it does not
     */
    Future<DeploymentStatus> check(UiDeployment deployment, String commitSha);
}
