package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.management.api.model.UiDeployment;

/**
 * Reports when a published UI serves at its hostname. Every site is served through what the
 * platform provisions once, so nothing is created per site; a deployment is
 * {@link DeploymentStatusType#PROVISIONING} from its first publish until its site serves the
 * published commit, which {@link #provision} starts checking and {@link #checkProvisioning}
 * advances.
 */
public interface UiDeploymentProvisioner {

    /**
     * Starts serving the deployment at its hostname. Returns the deployment with its status
     * set: ready when the site already serves the deployment's commit and its index,
     * provisioning until it does, failed with the reason when it cannot. Idempotent.
     *
     * @param deployment the deployment, already persisted with its label as id, its files
     *                   uploaded and the commit they were published from recorded
     * @return a future emitting the deployment with its status
     */
    Future<UiDeployment> provision(UiDeployment deployment);

    /**
     * Advances a provisioning deployment: ready once the site serves the deployment's commit
     * and its index at its hostname, failed with the reason when it cannot, and provisioning,
     * with what was observed, while still pending.
     *
     * @param deployment a deployment whose status is provisioning
     * @return a future emitting the deployment with its status
     */
    Future<UiDeployment> checkProvisioning(UiDeployment deployment);

}
