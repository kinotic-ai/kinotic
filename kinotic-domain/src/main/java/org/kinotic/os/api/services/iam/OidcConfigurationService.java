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
     * Returns every enabled OIDC configuration with {@code platformWide=true}. These are the
     * Kinotic-managed social providers (Google, Microsoft, etc.) shown as login/signup
     * buttons to all users.
     */
    CompletableFuture<List<OidcConfiguration>> findEnabledPlatformWide();

}

