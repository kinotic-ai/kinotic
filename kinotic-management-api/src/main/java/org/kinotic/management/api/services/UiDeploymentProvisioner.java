package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.management.api.model.UiDeployment;

/**
 * Creates and removes what serves a published UI at its hostname. Provisioning is
 * asynchronous: {@link #provision} starts it and {@link #checkProvisioning} advances a
 * deployment left {@link org.kinotic.management.api.model.UiDeploymentStatusType#PROVISIONING}
 * until it is ready or has failed.
 */
public interface UiDeploymentProvisioner {

    /**
     * Creates what every site of the organization shares, from the organization's storage,
     * which must be ready. Called when the organization is created, so its first site needs
     * nothing but its own resources; idempotent, and {@link #provision} creates the same
     * things again when it finds them missing.
     *
     * @param organization the organization, with its storage ready
     * @return a future completing once the shared resources exist
     */
    Future<Void> prepareOrganization(Organization organization);

    /**
     * Starts serving the deployment at its hostname, creating whatever the organization's
     * sites share when that is missing. Returns the deployment with its status set: ready when serving at
     * once, provisioning while the hostname is still being validated, failed with the reason
     * otherwise. Idempotent: provisioning a deployment again completes whatever an earlier
     * attempt left missing, and validates a hostname again whose validation lapsed.
     *
     * @param deployment   the deployment, already persisted with its label as id
     * @param organization the organization whose storage the site serves from
     * @return a future emitting the deployment with its status
     */
    Future<UiDeployment> provision(UiDeployment deployment, Organization organization);

    /**
     * Advances a provisioning deployment: ready once its hostname validates and its
     * certificate is deployed, failed with the reason when validation cannot succeed, and
     * unchanged while still pending.
     *
     * @param deployment a deployment whose status is provisioning
     * @return a future emitting the deployment with its status
     */
    Future<UiDeployment> checkProvisioning(UiDeployment deployment);

    /**
     * Stops serving the deployment and removes everything created for it alone; what the
     * organization's other sites share stays. What is already gone is not a failure.
     *
     * @param deployment the deployment being removed
     * @return a future completing when nothing of the site remains
     */
    Future<Void> remove(UiDeployment deployment);

}
