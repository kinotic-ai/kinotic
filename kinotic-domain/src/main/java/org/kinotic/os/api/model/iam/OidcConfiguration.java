package org.kinotic.os.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;
import java.util.List;

/**
 * A standalone, reusable OIDC provider configuration. Has no embedded scope or ownership —
 * the association between OIDC configs and the scopes that use them is stored on the consuming
 * entity (KinoticSystem, Organization, Application) via {@code oidcConfigurationIds}.
 * Platform configs are bootstrapped from {@code kinotic.oidc.platformProviders[]} via
 * {@code PlatformOidcBootstrap} and referenced from {@link org.kinotic.os.api.model.KinoticSystem};
 * per-org SSO configs are referenced from {@link org.kinotic.os.api.model.Organization}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OidcConfiguration implements Identifiable<String> {

    /**
     * Unique identifier for this configuration (UUID).
     */
    private String id;

    /**
     * Human-readable name for this configuration (e.g., "Google", "Corporate Okta").
     */
    private String name;

    /**
     * Provider identifier (e.g., "google", "okta", "azure-ad").
     */
    private String provider;

    /**
     * The OAuth 2.0 client identifier issued by the provider when Kinotic OS was registered as an application.
     * Sent during the authorization flow and used to validate the JWT's audience claim.
     */
    private String clientId;

    /**
     * Reference to the OAuth 2.0 client secret stored in {@code SecretStorageService}.
     * The value is looked up at use time via {@code secretStorageService.getSecret(secretScope, clientSecretRef)};
     * the secret itself never lives on this entity or in Elasticsearch. Null for public-client providers
     * (e.g. Apple, some pure SPA flows) that do not require a client secret.
     */
    private String clientSecretRef;

    /**
     * The browser-facing issuer URL. Must match the {@code iss} claim in JWTs from this provider.
     */
    private String authority;

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
     * Used to match incoming JWTs to the correct OIDC configuration by the user's email domain.
     */
    private List<String> domains;

    /**
     * Expected {@code aud} claim value in JWTs, or null for any audience. Typically the OAuth client ID.
     */
    private String audience;

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
     * When {@code false}, this configuration is ignored during authentication even if
     * it is referenced by a scope's {@code oidcConfigurationIds} list.
     */
    private boolean enabled;

    /**
     * How new identities from this provider are handled on first successful OIDC callback.
     * Defaults to {@link UserProvisioningMode#AUTO} — matches the "Continue with Google just
     * works" UX. Set to {@link UserProvisioningMode#REGISTRATION_REQUIRED} when admins want
     * users to accept ToS or pick options before their account is created.
     */
    private UserProvisioningMode provisioningMode = UserProvisioningMode.AUTO;

    /**
     * Timestamp when this configuration was first created.
     */
    private Date created;

    /**
     * Timestamp when this configuration was last modified.
     */
    private Date updated;

}
