package org.kinotic.os.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.os.api.model.OrganizationScoped;

import java.util.Date;
import java.util.List;

/**
 * An OIDC provider configuration owned by an {@link org.kinotic.os.api.model.Organization}.
 * "Where can this config be used" is expressed by inbound references rather than a flag
 * on the row itself:
 * <ul>
 *   <li>{@link org.kinotic.os.api.model.Organization#getSsoConfigId()} points at the
 *       org's single SSO config (when set).</li>
 *   <li>{@link org.kinotic.os.api.model.Application#getOidcConfigurationIds()} lists the
 *       configs each application accepts for application-level login.</li>
 * </ul>
 * The same config id may legitimately appear in both — e.g. an org uses the same Okta
 * tenant for org-admin SSO and for one of its customer-facing apps.
 *
 * <p>Kinotic-curated social configs (Google, Microsoft Live, etc.) live in
 * {@link OrgSignupOidcConfiguration}; platform-admin configs live in
 * {@link SystemOidcConfiguration} — both intentionally separate so the authorization
 * paths and lifecycle (admin UI vs. seeded migration) don't collide.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OidcConfiguration extends BaseOidcConfiguration implements OrganizationScoped<String> {

    /**
     * Owning organization. Auto-populated and enforced by {@code AbstractCrudService}
     * from the security context — callers don't set it directly outside elevated access.
     */
    private String organizationId;

    /**
     * Name of the OAuth client secret in the platform Azure Key Vault. The vault URI
     * is global config (one Key Vault per Kinotic deployment); the resolver always
     * fetches the latest version.
     */
    private String secretNameRef;

    /**
     * Optional cluster-internal URL used for backchannel token validation when the
     * browser-facing authority is not reachable from within the cluster.
     */
    private String backChannelAuthority;

    /**
     * The URI the provider will redirect to after a successful authorization.
     * Must be registered with the provider during Kinotic OS's application registration.
     */
    private String redirectUri;

    /**
     * The URI the provider will redirect to after the user logs out.
     * Optional — only used if the provider supports logout redirects.
     */
    private String postLogoutRedirectUri;

    /**
     * The URI used for silent token renewal in the browser (hidden iframe).
     * Optional — only used by frontends that implement silent refresh flows.
     */
    private String silentRedirectUri;

    /**
     * Email domains this provider handles (e.g., ["gmail.com", "company.com"]), or null for any domain.
     */
    private List<String> domains;

    /**
     * Dot-separated path to roles in JWT claims (e.g., "realm_access.roles" for Keycloak).
     * If set, roles are extracted from this path and included in the authenticated Participant.
     */
    private String rolesClaimPath;

    /**
     * Additional OAuth scopes to request beyond the defaults (typically "openid profile email").
     * Space-separated string passed to the provider during the authorization request.
     */
    private String additionalScopes;

    /**
     * How new identities from this provider are handled on first successful OIDC callback.
     * Defaults to {@link UserProvisioningMode#AUTO} — matches the "Continue with Google just
     * works" UX. Set to {@link UserProvisioningMode#REGISTRATION_REQUIRED} when admins want
     * users to accept ToS or pick options before their account is created.
     */
    private UserProvisioningMode provisioningMode = UserProvisioningMode.AUTO;
}
