package org.kinotic.domain.api.model.security;

/**
 * The outcome of a CLI poll of a device authorization grant.
 */
public enum PollStatus {
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
