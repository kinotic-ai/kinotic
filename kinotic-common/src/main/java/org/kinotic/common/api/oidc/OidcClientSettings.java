package org.kinotic.common.api.oidc;

/**
 * One client registration at a sign-in provider, as {@link OidcClientService#createClient} takes it. The provider is
 * described by its authority, whose OpenID Connect discovery document names its endpoints. A registration that names
 * the endpoints itself is an {@link OidcEndpointSettings}.
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

}
