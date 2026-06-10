package org.kinotic.gateway.internal.endpoints.rest;

/**
 * The {@code ?error=} codes the auth flows surface back to the SPA on failure. The SPA
 * matches on these same strings to render specific UX, so the catalog lives in one place
 * and new codes are added here rather than inlined as literals.
 */
public final class OidcErrorCodes {

    private OidcErrorCodes() {}

    /** IdP returned no code/state — usually a misconfigured redirect. */
    public static final String INVALID_CALLBACK = "invalid_callback";

    /** Session state didn't match the IdP's state param — possible CSRF / replay. */
    public static final String STATE_MISMATCH = "state_mismatch";

    /** {@code :configId} resolves to no enabled OIDC configuration for the flow's scope. */
    public static final String CONFIG_NOT_FOUND = "config_not_found";

    /** id_token failed validation (issuer, audience, sub missing, etc.). */
    public static final String INVALID_TOKEN = "invalid_token";

    /** id_token's email_verified claim is false (or missing for providers that require it). */
    public static final String EMAIL_NOT_VERIFIED = "email_not_verified";

    /** Auth code → token exchange at the IdP failed. Generic catch-all for callback failures not otherwise classified. */
    public static final String EXCHANGE_FAILED = "exchange_failed";

    /** Verified IdP identity has no IamUser in the target scope — login refuses to auto-create. */
    public static final String NO_ACCOUNT = "no_account";

    /** Found IamUser but {@code enabled=false}. */
    public static final String ACCOUNT_DISABLED = "account_disabled";

    /** IamUser lookup failed at the persistence layer — distinct from "not found". */
    public static final String LOOKUP_FAILED = "lookup_failed";

    /** Signup callback found an existing IamUser for the IdP identity — caller should log in instead. */
    public static final String ACCOUNT_EXISTS = "account_exists";

    /** Signup failed during pending sign-up creation. */
    public static final String SIGNUP_FAILED = "signup_failed";

    /** Invitation-accept OIDC identity carries a verified email that doesn't match the invited email. The invite is not consumed. */
    public static final String EMAIL_MISMATCH = "email_mismatch";

    /** Invitation token is unknown, expired, or already consumed. */
    public static final String INVITE_INVALID = "invite_invalid";
}
