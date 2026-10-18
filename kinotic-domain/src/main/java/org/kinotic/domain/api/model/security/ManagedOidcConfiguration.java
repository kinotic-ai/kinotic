package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A provider registration Kinotic made once and the platform operator manages, as opposed to one an
 * organization made for itself. Its client secret is one the operator placed in the platform's secret
 * storage under the name {@link #secretNameRef}, and it alone may name a provider's endpoints explicitly,
 * for a provider such as GitHub that publishes no OIDC discovery document. The concrete subtype states
 * whom the registration signs in: {@link OrgSignupOidcConfiguration} organization users at sign-up and
 * sign-in, {@link ApplicationSocialOidcConfiguration} the users of applications that offer it.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public abstract sealed class ManagedOidcConfiguration extends OidcConfiguration
        permits OrgSignupOidcConfiguration, ApplicationSocialOidcConfiguration {

    /**
     * Name of the OAuth client secret in the platform's secret storage — the Azure Key Vault of
     * the deployment, or a {@code KINOTIC_AKV_*} property in development; the latest version is
     * read at sign-in. {@code null} for a public client.
     */
    private String secretNameRef;

    /**
     * Full URL of the provider's OAuth 2.0 authorization endpoint. {@code null} when the
     * provider supports OIDC discovery, which supplies it from the authority.
     */
    private String authorizationUri;

    /**
     * Full URL of the provider's OAuth 2.0 token endpoint. {@code null} when the provider
     * supports OIDC discovery, which supplies it from the authority.
     */
    private String tokenUri;

    /**
     * Full URL of the identity endpoint queried with the access token when the provider
     * issues no id_token (e.g. {@code https://api.github.com/user}). {@code null} for
     * OIDC providers, whose identity claims come from the id_token.
     */
    private String userInfoUri;

    /**
     * Full URL of a GitHub-style emails endpoint — an array of {@code {email, primary,
     * verified}} — supplying the verified email when the {@link #userInfoUri} profile
     * omits it. {@code null} when the identity endpoint or id_token carries the email.
     */
    private String userEmailsUri;

    /**
     * Space-delimited OAuth scope string sent on the authorization request (RFC 6749 wire
     * format). {@code null} requests the standard OIDC {@code openid email profile}.
     */
    private String scopes;
}
