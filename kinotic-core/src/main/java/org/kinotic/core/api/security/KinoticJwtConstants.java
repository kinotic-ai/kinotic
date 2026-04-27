package org.kinotic.core.api.security;

/**
 * Shared constants for the Kinotic-issued JWT used as a STOMP-CONNECT ticket. Issuing and
 * validating code agree on these values; nothing outside the auth path should rely on them.
 */
public final class KinoticJwtConstants {

    private KinoticJwtConstants() {}

    /**
     * Mandatory {@code aud} claim on every Kinotic-minted JWT. Validators reject tokens
     * whose audience doesn't match — protects against IdP tokens (or any other unrelated
     * JWT) being replayed against the gateway.
     */
    public static final String AUDIENCE = "kinotic";
}
