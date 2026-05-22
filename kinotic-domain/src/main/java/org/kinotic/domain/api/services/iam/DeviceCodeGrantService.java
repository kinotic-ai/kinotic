package org.kinotic.domain.api.services.iam;

import org.kinotic.domain.api.model.iam.IamUser;

import java.util.Date;
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
     * A freshly started device authorization. The plaintext {@code deviceCode} and
     * {@code userCode} are available only here — the server stores only a hash of the
     * device code.
     */
    record DeviceCodeGrantStart(String deviceCode,
                                String userCode,
                                Date expiresAt,
                                int intervalSeconds) {}

    /** The outcome of a CLI poll. */
    enum PollStatus {
        /** The user has not yet approved the grant; the CLI should keep polling. */
        AUTHORIZATION_PENDING,
        /** The CLI polled faster than the allowed interval and should slow down. */
        SLOW_DOWN,
        /** The grant expired before it was approved. */
        EXPIRED,
        /** No usable grant matches the supplied device code. */
        INVALID,
        /** The grant was approved; {@link DeviceCodePollResult#user()} is populated. */
        APPROVED
    }

    /** Outcome of a CLI poll. {@link #user()} is non-null only when {@link #status()} is {@code APPROVED}. */
    record DeviceCodePollResult(PollStatus status, IamUser user) {}

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
