package org.kinotic.gateway.internal.endpoints.rest;

/**
 * Single-source constants for the auth/OIDC HTTP surface: frontend redirect targets,
 * API route bases, well-known scope ids, and the error codes the various login flows
 * surface back to the frontend on failure.
 *
 * <p>Anything visible to the browser (frontend redirect paths, error codes the SPA
 * matches on) lives here so renames can be tracked across server + client in one place.
 */
public final class OidcConstants {

    private OidcConstants() {}

    // ── Frontend redirect targets ─────────────────────────────────────────────

    /** Frontend path the user is redirected to after successful authentication. The Kinotic JWT is appended as a URL fragment (e.g. {@code /#token=<jwt>}). */
    public static final String LOGIN_SUCCESS_PATH = "/";

    /** Frontend path the user is redirected to for REGISTRATION_REQUIRED completion. The pending-registration token is appended as a query parameter (e.g. {@code /register?token=<verificationToken>}). */
    public static final String REGISTER_PATH = "/register";

    /** Frontend path the user is redirected to when login fails. The error code is appended as a query parameter (e.g. {@code /login?error=access_denied}). */
    public static final String LOGIN_ERROR_PATH = "/login";

    // ── API route bases ───────────────────────────────────────────────────────
    // Each handler mounts its routes under one base. URL-building helpers use these
    // same prefixes so route definitions and outbound redirect_uris stay in sync.

    /** Org login surface — used by {@code OrganizationLoginHandler}. */
    public static final String ORG_LOGIN_BASE = "/api/login";

    /** Org-signup completion endpoint that lives outside the {@link #SIGNUP_BASE} tree. */
    public static final String ORG_REGISTER_COMPLETE = "/api/register/complete";

    /** Org-signup surface — used by {@code OidcSignupHandler}. */
    public static final String SIGNUP_BASE = "/api/signup";

    /** Application-login surface — used by {@code ApplicationLoginHandler}. Includes the {@code :appId} path param. */
    public static final String APP_LOGIN_BASE = "/api/app/:appId/login";

    /** System-admin login surface — used by {@code SystemLoginHandler}. */
    public static final String SYSTEM_LOGIN_BASE = "/api/system/login";

    // ── Well-known scope ids ──────────────────────────────────────────────────

    /** Fixed {@code authScopeId} for SYSTEM-scoped IamUsers — Kinotic platform admins. */
    public static final String SYSTEM_SCOPE_ID = "kinotic";

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

    /** Signup failed during PendingRegistration creation. */
    public static final String ERR_SIGNUP_FAILED = "signup_failed";
}
