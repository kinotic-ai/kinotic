package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.security.BaseOidcConfiguration;
import org.kinotic.domain.api.model.security.OidcConfiguration;

import java.util.List;

// FIXME: add an OrganizationScopedServiceInterface
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
    Future<List<OidcConfiguration>> findEnabledByIds(List<String> ids, String orgId);

    /**
     * Returns the {@link OidcConfiguration} the given organization uses as its SSO
     * provider, or {@code null} if the org has no SSO configured. Sources from
     * {@link Organization#getSsoConfigId()} — structurally
     * one-per-org, no scope flag needed on the config row itself.
     */
    Future<OidcConfiguration> findOrgLoginConfig(String organizationId);

    /**
     * The enabled OIDC configurations a login scope offers its users, in the order they should be
     * presented. An application scope ({@code applicationId} set) offers the configurations that
     * application references; an organization scope offers the Kinotic-curated social providers
     * followed by the organization's own SSO configuration. Empty when the scope offers none.
     *
     * @param organizationId the organization the scope belongs to
     * @param applicationId the application within it, or {@code null} for the organization scope
     */
    Future<List<BaseOidcConfiguration>> findEnabledForScope(String organizationId, String applicationId);
}
