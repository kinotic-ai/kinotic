package org.kinotic.os.api.services.iam;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.os.api.model.iam.OidcConfiguration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Publish
public interface OidcConfigurationService extends IdentifiableCrudService<OidcConfiguration, String> {

    /**
     * Fetches the given OIDC configurations in a single request, returning only those that exist
     * and are enabled. Missing or disabled configurations are silently omitted.
     *
     * @param ids the configuration ids to load; may be null or empty
     * @return the enabled configurations, or an empty list if {@code ids} is null/empty
     */
    CompletableFuture<List<OidcConfiguration>> findEnabledByIds(List<String> ids);

    /**
     * Returns the {@link OidcConfiguration} the given organization uses as its SSO
     * provider, or {@code null} if the org has no SSO configured. Sources from
     * {@link org.kinotic.os.api.model.Organization#getSsoConfigId()} — structurally
     * one-per-org, no scope flag needed on the config row itself.
     */
    CompletableFuture<OidcConfiguration> findOrgLoginConfig(String organizationId);
}
