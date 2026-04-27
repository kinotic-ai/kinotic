package org.kinotic.os.api.services.iam;

import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.model.iam.PendingRegistration;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Turns a verified OIDC callback into either an authenticated {@link IamUser} or a
 * {@link PendingRegistration} awaiting a completion form, depending on the
 * {@link OidcConfiguration#getProvisioningMode()}.
 * <p>
 * Not a {@code @Publish} service — invoked in-process by the gateway's callback handler.
 */
public interface OidcUserProvisioningService {

    /**
     * Result of a provisioning attempt.
     */
    sealed interface Result permits Result.Authenticated, Result.NeedsRegistration {

        /** An {@link IamUser} is ready — the callback can mint a JWT and redirect. */
        record Authenticated(IamUser user) implements Result {}

        /**
         * No user exists yet and the config requires a registration form. The caller should
         * redirect the browser to the completion URL with this verification token.
         */
        record NeedsRegistration(PendingRegistration registration) implements Result {}
    }

    /**
     * Resolves or provisions an {@link IamUser} for a verified OIDC callback.
     *
     * @param config       the OIDC configuration that produced the callback
     * @param claims       the id_token claims (must contain {@code sub}, {@code email},
     *                     and {@code email_verified}; rejected if {@code email_verified=false})
     * @param authScopeType target scope type for the user record (SYSTEM / ORGANIZATION / APPLICATION)
     * @param authScopeId   target scope identifier
     * @return existing user (if already provisioned), newly-created user (AUTO mode), or a
     *         pending registration (REGISTRATION_REQUIRED mode)
     */
    CompletableFuture<Result> resolve(OidcConfiguration config,
                                      Map<String, Object> claims,
                                      String authScopeType,
                                      String authScopeId);
}
