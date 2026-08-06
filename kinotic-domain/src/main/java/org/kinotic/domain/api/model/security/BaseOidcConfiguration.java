package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * Baseline shape shared by every persisted OIDC provider configuration in the system.
 * Concrete subclasses ({@link OidcConfiguration}, {@link OrgSignupOidcConfiguration})
 * add the fields that distinguish their use case — org-scope and link metadata,
 * client secrets for confidential-client flows, etc.
 *
 * <p>Subclasses each live in their own Elasticsearch index and are looked up via a
 * dedicated CRUD service; this class is never persisted directly.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public abstract class BaseOidcConfiguration implements Identifiable<String> {

    private String id;

    /**
     * Human-readable name shown in the admin UI and on social-button labels.
     */
    private String name;

    /**
     * Provider kind selector — drives the Vert.x provider factory chosen at runtime.
     */
    private OidcProviderKind provider;

    /**
     * The OAuth 2.0 client identifier issued by the provider when Kinotic was registered
     * as an application. Sent during the authorization flow and used to validate the
     * JWT's audience claim.
     */
    private String clientId;

    /**
     * The browser-facing issuer URL. Must match the {@code iss} claim in JWTs from this provider.
     */
    private String authority;

    /**
     * Full URL of the provider's OAuth 2.0 authorization endpoint. {@code null} when the
     * provider supports OIDC discovery, which supplies it from {@link #authority}.
     */
    private String authorizationUri;

    /**
     * Full URL of the provider's OAuth 2.0 token endpoint. {@code null} when the provider
     * supports OIDC discovery, which supplies it from {@link #authority}.
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

    /**
     * Expected {@code aud} claim, or {@code null} to default to {@link #clientId}.
     */
    private String audience;

    /**
     * Disabled rows are kept in their table for audit/history but excluded from runtime
     * provider lists.
     */
    private boolean enabled;

    private Date created;

    private Date updated;

    /**
     * Name of the OAuth client secret in the platform Azure Key Vault. The vault URI
     * is global config (one Key Vault per Kinotic deployment); the resolver always
     * fetches the latest version.
     */
    private String secretNameRef;
}
