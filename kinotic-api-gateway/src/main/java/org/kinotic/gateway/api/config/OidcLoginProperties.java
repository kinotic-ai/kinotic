package org.kinotic.gateway.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration for the OIDC login HTTP routes ({@code /api/login/*}).
 * <p>
 * The public base URL used to build OIDC {@code redirect_uri}s is shared with the rest of
 * the platform via {@link org.kinotic.core.api.config.KinoticProperties#getAppBaseUrl()};
 * it is not duplicated here.
 */
@Getter
@Setter
@Accessors(chain = true)
public class OidcLoginProperties {

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
