package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.Organization;

/**
 * Provisions the storage an organization's deployments publish to: one account per
 * organization holding the {@code ui} container, recorded on the {@link Organization}.
 */
public interface OrganizationStorageProvisioner {

    /** The one container in an organization's account that its deployments publish UIs into. */
    String UI_CONTAINER = "ui";

    /**
     * Leaves the organization with usable storage, creating it on the first call and
     * returning at once on later ones. Idempotent: an organization whose provisioning failed
     * is provisioned again, and one whose provisioning is in flight is waited for. Fails when
     * the storage cannot be made ready, with the reason, which is also recorded on the
     * organization's storage status.
     *
     * @param organizationId the organization needing storage
     * @return a future emitting the organization with its storage
     *         {@link org.kinotic.domain.api.model.OrganizationStorageStatusType#READY}
     */
    Future<Organization> ensureStorage(String organizationId);

}
