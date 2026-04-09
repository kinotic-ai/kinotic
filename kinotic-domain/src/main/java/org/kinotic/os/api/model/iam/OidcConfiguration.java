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
 * <p>
 * Configs with {@code builtIn=true} are platform-provided (e.g., Google, Microsoft) and
 * immutable for organization/application administrators. They can be browsed and enabled
 * by adding the config ID to a scope entity's {@code oidcConfigurationIds} list.
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
     * When {@code true}, this is a platform-provided configuration that is immutable for
     * organization and application administrators. They can enable it but not modify or delete it.
     */
    private boolean builtIn;

    /**
     * The OAuth 2.0 client identifier issued by the provider when Kinotic OS was registered as an application.
     * Sent during the authorization flow and used to validate the JWT's audience claim.
     */
    private String clientId;

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
     * Email domains this provider handles (e.g., ["gmail.com", "company.com"]).
     * Used to match incoming JWTs to the correct OIDC configuration by the user's email domain.
     */
    private List<String> domains;

    /**
     * Expected {@code aud} claim value in JWTs. Typically the OAuth client ID.
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
     * Timestamp when this configuration was first created.
     */
    private Date created;

    /**
     * Timestamp when this configuration was last modified.
     */
    private Date updated;

}
