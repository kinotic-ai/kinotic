package org.kinotic.domain.api.services.iam;

import org.kinotic.domain.api.model.iam.DeviceCodeGrantStart;
import org.kinotic.domain.api.model.iam.DeviceCodePollResult;
import org.kinotic.domain.api.model.iam.UserParticipantIdentity;

import java.util.concurrent.CompletableFuture;

/**
 * Drives the server side of the OAuth 2.0 Device Authorization Grant (RFC 8628) for CLI
 * logins: starting a flow, polling it from the CLI, and approving it from an authenticated
 * browser.
 */
public interface DeviceCodeGrantService {

    /**
     * Starts a new device authorization grant and returns the codes the CLI needs to display
     * and poll with.
     *
     * @param deviceName optional name of the device starting the flow; becomes the label of
     *                   the token family issued when the grant is redeemed
     */
    CompletableFuture<DeviceCodeGrantStart> start(String deviceName);

    /**
     * Polls a pending grant by its {@code device_code}. Once the grant has been approved it
     * is consumed (deleted), and the approving user returned.
     *
     * @param deviceCode the plaintext device code issued by {@link #start()}
     */
    CompletableFuture<DeviceCodePollResult> poll(String deviceCode);

    /**
     * Binds an authenticated user to a pending grant identified by its {@code user_code}.
     * Fails if the code is unknown, already approved, or expired.
     *
     * @param userCode the code the user entered in the browser
     * @param identityId   id of the authenticated {@link UserParticipantIdentity} approving the grant
     */
    CompletableFuture<Void> approve(String userCode, String identityId);
}
