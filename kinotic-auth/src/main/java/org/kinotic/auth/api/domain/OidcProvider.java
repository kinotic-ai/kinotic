package org.kinotic.auth.api.domain;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OidcProvider {
    
    /**
     * Master switch for enabling/disabling the OIDC provider.
     */
    private boolean enabled;

    /**
     * The name of the OIDC provider.
     */
    private String provider;

    /**
     * The display name of the OIDC provider.
     */
    private String displayName;

    /**
     * The client ID of the OIDC provider.
     */
    private String clientId;

    /**
     * The authority of the OIDC provider. This should be fully qualified, 
     * i.e. 
     * https://your-authority.com/auth/realms/your-realm 
     *  or 
     * https://something.okta.com/oauth2/default
     */
    private String authority;

    /**
     * The redirect URI of the OIDC provider.
     */
    private String redirectUri;

    /**
     * The post logout redirect URI of the OIDC provider.
     */
    private String postLogoutRedirectUri;

    /**
     * The silent redirect URI of the OIDC provider.
     */
    private String silentRedirectUri;

    /**
     * The domains of the OIDC provider.
     */
    private List<String> domains;

    /**
     * The audience this service will expect for this OIDC provider.
     */
    private String audience;

    /**
     * The roles of the OIDC provider.
     */
    private List<String> roles;

    /**
     * Any additional metadata of the OIDC provider, will be added to the Participant metadata.
     */
    private Map<String, String> metadata;
    /**
     * Json path to the roles claim which must be a list of strings.
     * If not provided, we do not extract roles from the JWT token.
     * If nested, the path is a dot-separated string of the nested properties.
     * Example: "realm_access.roles"
     */
    private String rolesClaimPath;

    /**
     * Any additional scopes to be added to the OIDC provider.
     * If not provided, we do not add any additional scopes.
     * Example: "groups"
     */
    private String additionalScopes;

}
