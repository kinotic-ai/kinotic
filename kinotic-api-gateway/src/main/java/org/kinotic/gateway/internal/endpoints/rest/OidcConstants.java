package org.kinotic.gateway.internal.endpoints.rest;

/**
 * OIDC {@code ?error=} codes raised in one class and rendered into a redirect by another,
 * so neither can own the constant. Codes used by a single class are declared there instead.
 * Every code — wherever it is declared — is a wire-stable string the SPA matches on.
 */
public final class OidcConstants {

    private OidcConstants() {}

    /** id_token failed validation (issuer, audience, sub missing, etc.). */
    public static final String ERR_INVALID_TOKEN = "invalid_token";

    /** id_token's email_verified claim is false (or missing for providers that require it). */
    public static final String ERR_EMAIL_NOT_VERIFIED = "email_not_verified";

    /** {@code :configId} resolves to no enabled OIDC configuration for the flow's scope. */
    public static final String ERR_CONFIG_NOT_FOUND = "config_not_found";

    /** Auth code → token exchange at the IdP failed. Generic catch-all for callback failures not otherwise classified. */
    public static final String ERR_EXCHANGE_FAILED = "exchange_failed";
}
