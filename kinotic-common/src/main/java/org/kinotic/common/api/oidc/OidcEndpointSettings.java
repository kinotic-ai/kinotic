package org.kinotic.common.api.oidc;

/**
 * A client registration that can name its provider's endpoints in place of discovery, for a provider that publishes
 * no OpenID Connect discovery document, such as GitHub: the authorization and token endpoints, and the user info
 * endpoint that answers who signed in.
 */
public interface OidcEndpointSettings extends OidcClientSettings {

    /**
     * The provider's authorization endpoint, named together with {@link #getTokenUri()} in place of discovery;
     * {@code null} for a registration discovered from its authority.
     */
    String getAuthorizationUri();

    /**
     * The provider's token endpoint, named together with {@link #getAuthorizationUri()} in place of discovery;
     * {@code null} for a registration discovered from its authority.
     */
    String getTokenUri();

    /**
     * The endpoint that answers the signed-in person's profile for an access token. When named, the person's claims
     * are read from it in place of an id token.
     */
    String getUserInfoUri();

    /**
     * The endpoint that answers the signed-in person's email addresses as a GitHub-style array, each with
     * {@code email}, {@code primary} and {@code verified}. Read only when {@link #getUserInfoUri()} names an endpoint.
     */
    String getUserEmailsUri();

}
