package org.kinotic.common.api.oidc;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * One client registration at a sign-in provider, as {@link OidcClientService#createClient} takes it. A provider that
 * publishes an OpenID Connect discovery document is described by its {@code authority}. A provider that publishes
 * none, such as GitHub, names its {@code authorizationUri} and {@code tokenUri}, and the {@code userInfoUri} that
 * answers who signed in.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OidcClientSettings {

    /** The provider type. */
    private OidcProviderType provider;

    /** The issuer the provider's id tokens name, and the address its discovery document is read from. */
    private String authority;

    /** The registration's client id. */
    private String clientId;

    /** The registration's client secret, or {@code null} for a public client. */
    private String clientSecret;

    /** The audience id tokens must name, or {@code null} to accept any. */
    private String audience;

    /** The provider's authorization endpoint, set together with {@code tokenUri} in place of discovery. */
    private String authorizationUri;

    /** The provider's token endpoint, set together with {@code authorizationUri} in place of discovery. */
    private String tokenUri;

    /**
     * The endpoint that answers the signed-in person's profile for an access token. When set, the person's claims
     * are read from it in place of an id token.
     */
    private String userInfoUri;

    /**
     * The endpoint that answers the signed-in person's email addresses as a GitHub-style array, each with
     * {@code email}, {@code primary} and {@code verified}. Read only when {@code userInfoUri} is set.
     */
    private String userEmailsUri;

}
