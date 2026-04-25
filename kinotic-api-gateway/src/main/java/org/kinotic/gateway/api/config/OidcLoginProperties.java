package org.kinotic.gateway.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration for the OIDC login HTTP routes ({@code /api/login/*}).
 */
@Getter
@Setter
@Accessors(chain = true)
public class OidcLoginProperties {

    /**
     * Public base URL of the backend (no trailing slash). Used to construct the OIDC
     * {@code redirect_uri} that the IdP will redirect back to. Must match whatever is
     * registered with the IdP as the authorized redirect URI.
     * <p>
     * Example: {@code https://portal.kinotic.ai}. Callback URL becomes
     * {@code https://portal.kinotic.ai/api/login/callback/<providerKey>}.
     */
    private String baseUrl;

    /**
     * Frontend path the user is redirected to after successful authentication. The Kinotic
     * JWT is appended as a URL fragment (e.g. {@code /#token=<jwt>}). Fragments are not
     * sent to the server by browsers, so the token never appears in server access logs.
     */
    private String loginSuccessPath = "/";

    /**
     * Frontend path the user is redirected to for REGISTRATION_REQUIRED completion. The
     * pending-registration token is appended as a query parameter
     * (e.g. {@code /register?token=<verificationToken>}).
     */
    private String registerPath = "/register";

    /**
     * Frontend path the user is redirected to when login fails. The error code is appended
     * as a query parameter (e.g. {@code /login?error=access_denied}).
     */
    private String loginErrorPath = "/login";
}
