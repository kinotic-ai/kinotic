package org.kinotic.common.api.oidc;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * The values one sign-in is held to from the redirect to the provider until the callback that completes it. The
 * caller keeps them, usually in the browser's session, between {@link OidcClient#authorizationUrl} and
 * {@link OidcClient#verifiedClaims}.
 *
 * @param state        the {@code state} the callback must return
 * @param nonce        the {@code nonce} the id token must carry
 * @param pkceVerifier the PKCE code verifier the code is redeemed with
 */
public record OidcAuthorization(String state, String nonce, String pkceVerifier) {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** @return an authorization for a new sign-in, with a fresh random state, nonce and PKCE code verifier */
    public static OidcAuthorization create() {
        return new OidcAuthorization(token(32), token(32), token(64));
    }

    private static String token(int bytes) {
        byte[] ret = new byte[bytes];
        RANDOM.nextBytes(ret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(ret);
    }

}
