package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.os.api.model.Organization;
import org.kinotic.os.api.model.iam.OidcConfiguration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Publish
public interface OrganizationService extends IdentifiableCrudService<Organization, String> {

    /**
     * Returns the enabled OIDC configurations registered on the given organization.
     *
     * @param organizationId the id of the organization
     * @return the enabled configurations, or an empty list if the organization is not
     *         found or has no configurations attached
     */
    CompletableFuture<List<OidcConfiguration>> getOidcConfigurations(String organizationId);

}

