package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.model.security.OrganizationOidcConfiguration;
import org.kinotic.domain.api.model.security.PlatformOidcConfiguration;

import java.util.List;

/**
 * The OIDC provider configurations of the platform and of its organizations. A write is held to its
 * kind's owner: a {@link PlatformOidcConfiguration} is saved or deleted by a system participant only,
 * an {@link OrganizationOidcConfiguration} by an organization participant of the organization it
 * names. The finders that serve the sign-in flows, which run before a participant is bound, check no
 * participant and say so.
 */
public interface OidcConfigurationService extends IdentifiableCrudService<OidcConfiguration, String> {

    /**
     * Every enabled platform configuration — the source of the social button list rendered on
     * signup and login pages. Checks no participant.
     */
    Future<List<PlatformOidcConfiguration>> findEnabledPlatformProviders();

    /**
     * The single enabled platform configuration of the given provider kind, or {@code null} when none
     * is configured, for the social-button start endpoints resolving the {@code :provider} path param.
     * Checks no participant.
     */
    Future<PlatformOidcConfiguration> findEnabledPlatformByProvider(OidcProviderKind provider);

    /**
     * The platform configuration {@code id}, enabled or not, or {@code null} when no platform
     * configuration has that id, for the social callbacks. Checks no participant.
     *
     * @param id the configuration id
     */
    Future<PlatformOidcConfiguration> findPlatformById(String id);

    /**
     * Fetches the given OIDC configurations in a single request, returning only those that
     * belong to {@code orgId} and whose {@code enabled} flag is true. Missing or disabled
     * configurations are silently omitted. Checks no participant.
     *
     * @param ids the configuration ids to load; must be non-null and non-empty
     * @param orgId the organization that owns the configurations
     * @return the enabled configurations
     */
    Future<List<OrganizationOidcConfiguration>> findEnabledByIds(List<String> ids, String orgId);

    /**
     * The OIDC configuration {@code id} of organization {@code organizationId}, enabled or not, or
     * {@code null} when the organization has none by that id. Checks no participant.
     *
     * @param id             the configuration id
     * @param organizationId the organization that owns the configuration
     */
    Future<OrganizationOidcConfiguration> findById(String id, String organizationId);

    /**
     * Returns the {@link OrganizationOidcConfiguration} the given organization uses as its SSO
     * provider, or {@code null} if the org has no SSO configured. Sources from
     * {@link Organization#getSsoConfigId()} — structurally one-per-org, no scope flag needed on the
     * config row itself. Checks no participant.
     */
    Future<OrganizationOidcConfiguration> findOrgLoginConfig(String organizationId);

    /**
     * The enabled OIDC configurations a login scope offers its users, in the order they should be
     * presented. An application scope ({@code applicationId} set) offers the configurations that
     * application references; an organization scope offers the Kinotic-curated social providers
     * followed by the organization's own SSO configuration. Empty when the scope offers none.
     * Checks no participant.
     *
     * @param organizationId the organization the scope belongs to
     * @param applicationId the application within it, or {@code null} for the organization scope
     */
    Future<List<OidcConfiguration>> findEnabledForScope(String organizationId, String applicationId);

    /**
     * Saves the configuration as {@link #save(Object)} does and makes {@code clientSecret} the secret it
     * sends its provider, replacing the one it had. The secret is held in the owning organization's scope
     * of the secret storage, keyed by the configuration's id, so it is never a field of the configuration
     * and no other organization's configuration can read it. A sign-in through the configuration picks
     * the new secret up on its next flow.
     *
     * @param configuration the configuration, owned by the caller's organization
     * @param clientSecret  the client secret's value, or {@code null} or blank for a public client, whose
     *                      stored secret is deleted
     * @return the configuration as saved
     */
    Future<OrganizationOidcConfiguration> save(OrganizationOidcConfiguration configuration, String clientSecret);

    /**
     * The client secret of {@code configuration}, or {@code null} for a public client. Reads the owning
     * organization's scope of the secret storage only. Checks no participant.
     *
     * @param configuration the configuration as persisted, naming its organization
     */
    Future<String> findClientSecret(OrganizationOidcConfiguration configuration);
}
