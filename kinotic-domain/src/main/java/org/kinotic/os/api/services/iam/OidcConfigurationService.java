package org.kinotic.os.api.services.iam;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.os.api.model.iam.OidcConfiguration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Publish
public interface OidcConfigurationService extends IdentifiableCrudService<OidcConfiguration, String> {

    /**
     * Returns the enabled OIDC configurations for the given auth scope. Dispatches to
     * the service that owns each scope entity (System, Organization, or Application)
     * to load the configurations attached to that scope.
     *
     * @param authScopeType one of the well-known {@code AuthScopeType} values
     * @param authScopeId   the scope identifier (e.g. "kinotic" for SYSTEM, an org id, or an app id)
     * @return the enabled configurations, or an empty list if none are attached
     */
    CompletableFuture<List<OidcConfiguration>> getConfigsForScope(String authScopeType, String authScopeId);

    /**
     * Fetches the given OIDC configurations in a single request, returning only those that exist
     * and are enabled. Missing or disabled configurations are silently omitted.
     *
     * @param ids the configuration ids to load; may be null or empty
     * @return the enabled configurations, or an empty list if {@code ids} is null/empty
     */
    CompletableFuture<List<OidcConfiguration>> findEnabledByIds(List<String> ids);

}

