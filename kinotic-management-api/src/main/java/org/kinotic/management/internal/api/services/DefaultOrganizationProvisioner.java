package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.services.OrganizationProvisioner;
import org.kinotic.management.api.services.OrganizationStorageProvisioner;
import org.kinotic.management.api.services.UiDeploymentProvisioner;
import org.springframework.stereotype.Component;

/**
 * Gives a new organization what its deployments publish to: its storage, then what the
 * serving layer needs to read that storage. Both take minutes on Azure, so they run in the
 * background from the moment the organization is created and record their outcome on it; a
 * deployment that finds them missing provisions them again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultOrganizationProvisioner implements OrganizationProvisioner {

    private final OrganizationStorageProvisioner organizationStorageProvisioner;
    private final UiDeploymentProvisioner uiDeploymentProvisioner;

    @Override
    public Future<Void> provision(Organization organization) {
        // started here, awaited by nobody: the organization record carries the outcome
        Future.succeededFuture(organization.getId())
              .compose(organizationStorageProvisioner::ensureStorage)
              .compose(uiDeploymentProvisioner::prepareOrganization)
              .onSuccess(v -> log.info("Organization {} is provisioned", organization.getId()))
              .onFailure(error -> log.warn("Organization {} could not be provisioned; the next deployment of a UI provisions it again: {}",
                                           organization.getId(), error.getMessage()));
        return Future.succeededFuture();
    }

}
