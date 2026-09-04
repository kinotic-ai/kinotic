package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.services.OrganizationProvisioner;
import org.kinotic.management.api.services.DeploymentOperationsProxy;
import org.springframework.stereotype.Component;

/**
 * Gives an organization what its deployments publish to, by having the system server run
 * its provisioning job. The job records its outcome on the organization and shows in the
 * console as the organization's provisioning run.
 */
@Component
@RequiredArgsConstructor
public class DefaultOrganizationProvisioner implements OrganizationProvisioner {

    private final DeploymentOperationsProxy operations;

    @Override
    public Future<Void> provision(Organization organization) {
        return operations.provisionOrganization(organization.getId()).mapEmpty();
    }

}
