package org.kinotic.common.api.oidc;

import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

/**
 * A client of one registration at a sign-in provider, for signing a person in with the authorization code flow and
 * PKCE: {@link #authorizationUrl} starts a sign-in and {@link #verifiedClaims} completes it with the code the
 * provider returns.
 */
public interface OidcClient {

    /**
     * Builds the provider's authorization URL to send the browser to for the sign-in {@code authorization} holds.
     *
     * @param authorization the sign-in's {@link OidcAuthorization#create() authorization}
     * @param callbackUrl   the URL the provider redirects back to
     * @param scopes        the scopes to request
     * @return the authorization URL
     */
    String authorizationUrl(OidcAuthorization authorization, String callbackUrl, List<String> scopes);

    /**
     * Redeems the code the provider returned to {@code callbackUrl} and returns the claims of the person who signed
     * in ({@code sub}, {@code email}, {@code email_verified}, {@code name}, …). The claims are those of the id token
     * once its issuer, audience, nonce and expiry check out, or, for a registration that sets
     * {@link OidcEndpointSettings#getUserInfoUri() userInfoUri}, those its profile and email endpoints answer. The
     * caller must have matched the callback's {@code state} to {@link OidcAuthorization#state()} first.
     *
     * @param code          the authorization code
     * @param callbackUrl   the redirect URI the sign-in was started with
     * @param authorization the authorization the sign-in was started with
     * @return the claims, or a failure: {@link InvalidIdTokenException} when the id token fails verification
     */
    Future<Map<String, Object>> verifiedClaims(String code, String callbackUrl, OidcAuthorization authorization);

    /** Releases the client and the resources it holds. */
    void close();

}
