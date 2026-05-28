package org.kinotic.os.api.services.iam;

import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.iam.OidcConfiguration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface OidcConfigurationService extends IdentifiableCrudService<OidcConfiguration, String> {

    /**
     * Fetches the given OIDC configurations in a single request, returning only those that
     * belong to {@code orgId} and whose {@code enabled} flag is true. Missing or disabled
     * configurations are silently omitted.
     *
     * @param ids the configuration ids to load; must be non-null and non-empty
     * @param orgId the organization that owns the configurations
     * @return the enabled configurations
     */
    CompletableFuture<List<OidcConfiguration>> findEnabledByIds(List<String> ids, String orgId);

    /**
     * Returns the {@link OidcConfiguration} the given organization uses as its SSO
     * provider, or {@code null} if the org has no SSO configured. Sources from
     * {@link Organization#getSsoConfigId()} — structurally
     * one-per-org, no scope flag needed on the config row itself.
     */
    CompletableFuture<OidcConfiguration> findOrgLoginConfig(String organizationId);
}
