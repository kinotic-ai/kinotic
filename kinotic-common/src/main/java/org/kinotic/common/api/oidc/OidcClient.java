package org.kinotic.common.api.oidc;

import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

/**
 * A client of one registration at a sign-in provider, for signing a person in with the authorization code flow and
 * PKCE: {@link #authorize} starts a sign-in and {@link #verifiedClaims} completes it with the code the provider
 * returns.
 */
public interface OidcClient {

    /**
     * Starts a sign-in with a fresh state, nonce and PKCE code verifier.
     *
     * @param callbackUrl the URL the provider redirects back to
     * @param scopes      the scopes to request
     * @return the authorization URL and the values the callback must present
     */
    OidcAuthorization authorize(String callbackUrl, List<String> scopes);

    /**
     * Redeems the code the provider returned to {@code callbackUrl} and returns the claims of the person who signed
     * in ({@code sub}, {@code email}, {@code email_verified}, {@code name}, …). The claims are those of the id token
     * once its issuer, audience and nonce check out, or, for a registration that sets
     * {@link OidcClientSettings#getUserInfoUri() userInfoUri}, those its profile and email endpoints answer. The
     * caller must have matched the callback's {@code state} to {@link OidcAuthorization#state()} first.
     *
     * @param code         the authorization code
     * @param callbackUrl  the redirect URI the sign-in was started with
     * @param pkceVerifier the sign-in's {@link OidcAuthorization#pkceVerifier()}
     * @param nonce        the sign-in's {@link OidcAuthorization#nonce()}
     * @return the claims, or a failure: {@link InvalidIdTokenException} when the id token fails verification
     */
    Future<Map<String, Object>> verifiedClaims(String code, String callbackUrl, String pkceVerifier, String nonce);

    /** Releases the client and the resources it holds. */
    void close();

}
