package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.domain.api.model.Organization;

/**
 * Provisions the storage an organization's deployments publish to: one account per
 * organization holding the {@code sites} container, recorded on the {@link Organization}.
 * Provisioning runs when the organization is created; a deployment only reads the outcome.
 */
public interface OrganizationStorageProvisioner {

    /** The one container in an organization's account that its deployments publish UIs into. */
    // container names are 3 to 63 characters, which rules out "ui"; Azurite does not check
    String UI_CONTAINER = "sites";

    /**
     * Leaves the organization with usable storage: returns at once when it is ready, waits
     * for provisioning in flight, and provisions when there is none or the last attempt
     * failed. Fails when the storage cannot be made ready, with the reason, which is also
     * recorded on the organization's storage status.
     *
     * @param organizationId the organization needing storage
     * @return a future emitting the organization with its storage
     *         {@link org.kinotic.domain.api.model.DeploymentStatusType#READY}
     */
    Future<Organization> ensureStorage(String organizationId);

}
