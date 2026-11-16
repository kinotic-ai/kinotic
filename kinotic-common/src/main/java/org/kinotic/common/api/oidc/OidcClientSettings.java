package org.kinotic.common.api.oidc;

/**
 * One client registration at a sign-in provider, as {@link OidcClientService#createClient} takes it. A provider that
 * publishes an OpenID Connect discovery document is described by its authority. A provider that publishes none, such
 * as GitHub, names its authorization and token endpoints, and the user info endpoint that answers who signed in.
 */
public interface OidcClientSettings {

    /** The provider type. */
    OidcProviderType getProvider();

    /** The issuer the provider's id tokens name, and the address its discovery document is read from. */
    String getAuthority();

    /** The registration's client id. */
    String getClientId();

    /** The audience id tokens must name, or {@code null} to accept any. */
    String getAudience();

    /**
     * The provider's authorization endpoint, named together with {@link #getTokenUri()} in place of discovery;
     * {@code null} for a registration discovered from its authority.
     */
    default String getAuthorizationUri() {
        return null;
    }

    /**
     * The provider's token endpoint, named together with {@link #getAuthorizationUri()} in place of discovery;
     * {@code null} for a registration discovered from its authority.
     */
    default String getTokenUri() {
        return null;
    }

    /**
     * The endpoint that answers the signed-in person's profile for an access token. When named, the person's claims
     * are read from it in place of an id token.
     */
    default String getUserInfoUri() {
        return null;
    }

    /**
     * The endpoint that answers the signed-in person's email addresses as a GitHub-style array, each with
     * {@code email}, {@code primary} and {@code verified}. Read only when {@link #getUserInfoUri()} names an endpoint.
     */
    default String getUserEmailsUri() {
        return null;
    }

}
