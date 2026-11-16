package org.kinotic.common.api.oidc;

/**
 * A started sign-in: the provider's authorization URL to send the browser to, and the values the callback that
 * completes it must present. The caller keeps the values, usually in the browser's session, until the callback.
 *
 * @param url          the provider's authorization URL
 * @param state        the {@code state} the callback must return
 * @param nonce        the {@code nonce} the id token must carry, for {@link OidcClient#verifiedClaims}
 * @param pkceVerifier the PKCE code verifier, for {@link OidcClient#verifiedClaims}
 */
public record OidcAuthorization(String url, String state, String nonce, String pkceVerifier) {
}
