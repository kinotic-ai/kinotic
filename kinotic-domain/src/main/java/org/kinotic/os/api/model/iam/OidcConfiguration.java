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

    private String clientId;

    /**
     * The browser-facing issuer URL. Must match the {@code iss} claim in JWTs from this provider.
     */
    private String authority;

    /**
     * Optional cluster-internal URL used for backchannel token validation when the
     * browser-facing authority is not reachable from within the cluster.
     */
    private String backchannelAuthority;

    private String redirectUri;

    private String postLogoutRedirectUri;

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

    private String additionalScopes;

    private boolean enabled;

    private Date created;

    private Date updated;

}
