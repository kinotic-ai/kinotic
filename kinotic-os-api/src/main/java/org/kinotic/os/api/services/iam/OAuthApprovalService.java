package org.kinotic.os.api.services.iam;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.iam.PendingOAuthAuthorization;

import java.util.concurrent.CompletableFuture;

/**
 * Browser-invoked service for the OAuth 2.1 consent step. The SPA consent page describes the
 * pending authorization request to the signed-in user, then approves or denies it; either
 * decision returns the client's redirect URL the browser must navigate to. The framework
 * injects the calling {@link Participant} so an approval is bound to that user.
 */
@Publish
public interface OAuthApprovalService {

    /**
     * Describes the authorization request awaiting consent, for display before the user decides.
     *
     * @param requestId the request id from the consent page URL
     * @return a {@link CompletableFuture} emitting the pending request, failing if it is
     *         unknown, expired, or already decided
     */
    CompletableFuture<PendingOAuthAuthorization> describe(String requestId);

    /**
     * Approves the authorization request as the calling participant's user.
     *
     * @param requestId the request id from the consent page URL
     * @return a {@link CompletableFuture} emitting the redirect URL carrying the authorization code
     */
    CompletableFuture<String> approve(String requestId, Participant participant);

    /**
     * Denies the authorization request.
     *
     * @param requestId the request id from the consent page URL
     * @return a {@link CompletableFuture} emitting the redirect URL carrying {@code error=access_denied}
     */
    CompletableFuture<String> deny(String requestId);

    /**
     * Approves the pending RFC 8628 device grant identified by {@code userCode} as the calling
     * participant's user. Fails if the code is unknown, already approved, or expired.
     *
     * @param userCode the code the user confirmed on the device page
     */
    CompletableFuture<Void> approveDevice(String userCode, Participant participant);
}
