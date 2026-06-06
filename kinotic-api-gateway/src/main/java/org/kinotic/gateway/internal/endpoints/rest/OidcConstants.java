package org.kinotic.gateway.internal.endpoints.rest;

/**
 * The browser-facing auth/OIDC contract in one place: the frontend redirect targets the
 * backend sends the browser to, and the {@code ?error=} codes the login flows surface
 * back to the SPA on failure. The SPA matches on these same strings, so co-locating them
 * keeps the cross-boundary contract scannable and easy to keep in step on a rename.
 */
public final class OidcConstants {

    private OidcConstants() {}

    // ── Frontend redirect targets ─────────────────────────────────────────────

    /** Frontend path (resolved against {@code kinotic.domain.appBaseUrl}) the user is redirected to after successful authentication. */
    public static final String LOGIN_SUCCESS_PATH = "/";

    /** Frontend path the user is redirected to in order to complete sign-up by naming their organization. The pending sign-up's verification token is appended as a query parameter (e.g. {@code /register?token=<verificationToken>}). */
    public static final String REGISTER_PATH = "/register";

    /** Frontend path the user is redirected to when login fails. The error code is appended as a query parameter (e.g. {@code /login?error=access_denied}). */
    public static final String LOGIN_ERROR_PATH = "/login";

    /** Frontend path where the user enters/confirms a CLI device {@code user_code} (the RFC 8628 verification URI). */
    public static final String DEVICE_VERIFICATION_PATH = "/device";

    // ── Error codes ───────────────────────────────────────────────────────────
    // Wire-stable strings the frontend matches on to render specific UX. Add new
    // codes here rather than inlining string literals so the catalog stays scannable.

    /** IdP returned no code/state — usually a misconfigured redirect. */
    public static final String ERR_INVALID_CALLBACK = "invalid_callback";

    /** Session state didn't match the IdP's state param — possible CSRF / replay. */
    public static final String ERR_STATE_MISMATCH = "state_mismatch";

    /** {@code :configId} from the callback path resolves to no enabled OidcConfiguration. */
    public static final String ERR_CONFIG_NOT_FOUND = "config_not_found";

    /** id_token failed validation (issuer, audience, sub missing, etc.). */
    public static final String ERR_INVALID_TOKEN = "invalid_token";

    /** id_token's email_verified claim is false (or missing for providers that require it). */
    public static final String ERR_EMAIL_NOT_VERIFIED = "email_not_verified";

    /** Auth code → token exchange at the IdP failed. Generic catch-all for callback failures not otherwise classified. */
    public static final String ERR_EXCHANGE_FAILED = "exchange_failed";

    /** Verified IdP identity has no IamUser in the target scope — login refuses to auto-create. */
    public static final String ERR_NO_ACCOUNT = "no_account";

    /** Found IamUser but {@code enabled=false}. */
    public static final String ERR_ACCOUNT_DISABLED = "account_disabled";

    /** IamUser lookup failed at the persistence layer — distinct from "not found". */
    public static final String ERR_LOOKUP_FAILED = "lookup_failed";

    /** Signup callback found an existing IamUser for the IdP identity — caller should log in instead. */
    public static final String ERR_ACCOUNT_EXISTS = "account_exists";

    /** Signup failed during pending sign-up creation. */
    public static final String ERR_SIGNUP_FAILED = "signup_failed";
}
