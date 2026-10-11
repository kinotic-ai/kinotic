package org.kinotic.domain.api.model.security;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * A persisted OIDC provider configuration: the provider, the client Kinotic registered with it, and
 * the endpoints and claims a sign-in through it uses. The concrete subtype states who owns the
 * configuration and carries the fields only that owner has — {@link PlatformOidcConfiguration} for
 * a provider the platform operator curates, {@link OrganizationOidcConfiguration} for one an
 * organization configures for itself. The client secret is never a field of a configuration: each
 * subtype says where its secret is held and who may read it. All subtypes share one id space and
 * one index; {@link #getType()} is the persisted discriminator the document is deserialized by.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PlatformOidcConfiguration.class, name = "PLATFORM"),
        @JsonSubTypes.Type(value = OrganizationOidcConfiguration.class, name = "ORGANIZATION")
})
public abstract sealed class OidcConfiguration implements Identifiable<String>
        permits PlatformOidcConfiguration, OrganizationOidcConfiguration {

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
     * Who owns this configuration; fixed by the concrete subtype and persisted as the polymorphic
     * discriminator of the document.
     */
    public abstract OidcConfigurationType getType();
}
