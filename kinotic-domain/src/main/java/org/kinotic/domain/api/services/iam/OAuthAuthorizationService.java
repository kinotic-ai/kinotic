package org.kinotic.domain.api.services.iam;

import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.PendingOAuthAuthorization;
import java.util.concurrent.CompletableFuture;

/**
 * The OAuth 2.1 authorization-server logic behind the gateway's authorize and token endpoints and
 * the browser consent page. Clients are public and identify themselves with a Client ID Metadata
 * Document URL (draft-ietf-oauth-client-id-metadata-document), so none is stored here. Every grant
 * is a PKCE S256 authorization code: single-use, short-lived, stored hashed, and bound to the
 * client, redirect URI, and code challenge presented when the flow began.
 */
public interface OAuthAuthorizationService {

    /**
     * Begins an authorization-code flow: resolves the client's metadata document, validates the
     * redirect URI against it, and stores the request for the consent page to act on.
     *
     * @param clientId      the requesting client's Client ID Metadata Document URL
     * @param redirectUri   must exactly match a redirect URI the client's metadata document registers
     * @param codeChallenge the PKCE S256 challenge the eventual code exchange must prove
     * @param scope         the requested scope, or {@code null}
     * @param resource      the RFC 8707 resource the request is bound to, or {@code null}
     * @param state         client CSRF value echoed on the redirect, or {@code null}
     * @return a {@link CompletableFuture} emitting the request id the consent page approves or denies with
     */
    CompletableFuture<String> createAuthorizationRequest(String clientId,
                                                         String redirectUri,
                                                         String codeChallenge,
                                                         String scope,
                                                         String resource,
                                                         String state);

    /**
     * Describes a request awaiting consent, for display on the consent page.
     *
     * @param requestId the id returned by {@link #createAuthorizationRequest}
     * @return a {@link CompletableFuture} emitting the pending request, failing when it is
     *         unknown, expired, or already decided
     */
    CompletableFuture<PendingOAuthAuthorization> findPending(String requestId);

    /**
     * Binds the approving user to the request and mints its single-use authorization code.
     *
     * @param requestId the id returned by {@link #createAuthorizationRequest}
     * @param userId    the approving {@link IamUser}'s id
     * @return a {@link CompletableFuture} emitting the full redirect URL, carrying the code and
     *         the client's {@code state}, that the browser must navigate to
     */
    CompletableFuture<String> approve(String requestId, String userId);

    /**
     * Rejects the request and consumes it.
     *
     * @param requestId the id returned by {@link #createAuthorizationRequest}
     * @return a {@link CompletableFuture} emitting the full redirect URL carrying
     *         {@code error=access_denied} and the client's {@code state}
     */
    CompletableFuture<String> deny(String requestId);

    /**
     * Exchanges an authorization code for its approving user, consuming the grant so the code
     * can never be replayed. Verifies the code, its expiry, the client and redirect URI it was
     * issued to, and the PKCE verifier against the challenge the flow began with.
     *
     * @param code         the plaintext authorization code from the redirect
     * @param clientId     the client presenting the code
     * @param redirectUri  the redirect URI the code was issued for
     * @param codeVerifier the PKCE verifier whose S256 hash must equal the stored challenge
     * @return a {@link CompletableFuture} emitting the enabled approving user
     */
    CompletableFuture<IamUser> exchangeCode(String code,
                                            String clientId,
                                            String redirectUri,
                                            String codeVerifier);
}
