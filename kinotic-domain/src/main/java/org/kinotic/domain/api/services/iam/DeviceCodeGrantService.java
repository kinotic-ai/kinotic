package org.kinotic.domain.api.services.iam;

import org.kinotic.domain.api.model.iam.IamUser;

import java.util.concurrent.CompletableFuture;

/**
 * Drives the server side of the OAuth 2.0 Device Authorization Grant (RFC 8628) for CLI
 * logins: starting a flow, polling it from the CLI, and approving it from an authenticated
 * browser.
 * <p>
 * Not a {@code @Publish} service — called in-process from the gateway's HTTP handlers.
 */
public interface DeviceCodeGrantService {

    /**
     * Starts a new device authorization grant and returns the codes the CLI needs to display
     * and poll with.
     */
    CompletableFuture<DeviceCodeGrantStart> start();

    /**
     * Polls a pending grant by its {@code device_code}. Once the grant has been approved it
     * is consumed (deleted) and the approving user returned.
     *
     * @param deviceCode the plaintext device code issued by {@link #start()}
     */
    CompletableFuture<DeviceCodePollResult> poll(String deviceCode);

    /**
     * Binds an authenticated user to a pending grant identified by its {@code user_code}.
     * Fails if the code is unknown, already approved, or expired.
     *
     * @param userCode the code the user entered in the browser
     * @param userId   id of the authenticated {@link IamUser} approving the grant
     */
    CompletableFuture<Void> approve(String userCode, String userId);
}
