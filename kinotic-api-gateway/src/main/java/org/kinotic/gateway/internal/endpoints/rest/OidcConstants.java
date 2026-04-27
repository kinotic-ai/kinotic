package org.kinotic.gateway.internal.endpoints.rest;

public final class OidcConstants {

    private OidcConstants() {}

    /** Frontend path the user is redirected to after successful authentication. The Kinotic JWT is appended as a URL fragment (e.g. {@code /#token=<jwt>}). */
    public static final String LOGIN_SUCCESS_PATH = "/";

    /** Frontend path the user is redirected to for REGISTRATION_REQUIRED completion. The pending-registration token is appended as a query parameter (e.g. {@code /register?token=<verificationToken>}). */
    public static final String REGISTER_PATH = "/register";

    /** Frontend path the user is redirected to when login fails. The error code is appended as a query parameter (e.g. {@code /login?error=access_denied}). */
    public static final String LOGIN_ERROR_PATH = "/login";
}
