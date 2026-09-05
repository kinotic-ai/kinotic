package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.domain.api.model.Organization;

public interface OrganizationService extends IdentifiableCrudService<Organization, String> {

    /**
     * Runs every {@link OrganizationProvisioner} on the organization: once when the
     * organization is created, and again whenever an operator asks. Completes once each
     * provisioner's work is under way; the outcome is recorded on the organization.
     *
     * @param organizationId the organization to provision
     * @return a future emitting the organization as the provisioners left it
     * @throws IllegalArgumentException if no organization has the id
     */
    Future<Organization> provision(String organizationId);

}
