package org.kinotic.domain.api.rest.support;

import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.utils.HostLabelUtil;

/**
 * The one callback URL each configuration a login offers is registered with at its provider; every flow through the
 * configuration, whatever its purpose, starts with this URL and returns to it. An organization's login serves the
 * social callback for the system's org sign-up registrations and the SSO callback for an organization's own
 * configurations under the management server's API base URL; an application's login serves the OIDC callback for
 * the organization's application and tenant configurations on the application's own API host.
 */
public final class OidcCallbacks {

    public static final String SOCIAL_CALLBACK_PATH = "/api/auth/org/login/social/callback/";
    public static final String SSO_CALLBACK_PATH = "/api/auth/org/login/sso/callback/";
    public static final String APPLICATION_CALLBACK_PATH = "/api/auth/app/login/oidc/callback/";

    private OidcCallbacks() {}

    /** The callback of the system's org sign-up registration {@code configId}, under the management server's {@code apiBaseUrl}. */
    public static String socialCallbackUrl(String apiBaseUrl, String configId) {
        return apiBaseUrl + SOCIAL_CALLBACK_PATH + configId;
    }

    /** The callback of the organization's own configuration {@code configId}, under the management server's {@code apiBaseUrl}. */
    public static String ssoCallbackUrl(String apiBaseUrl, String configId) {
        return apiBaseUrl + SSO_CALLBACK_PATH + configId;
    }

    /** The callback of {@code config}, one of the configurations an organization's login offers, under the management server's {@code apiBaseUrl}. */
    public static String organizationCallbackUrl(String apiBaseUrl, OidcConfiguration config) {
        return config instanceof OrgSignupOidcConfiguration
                ? socialCallbackUrl(apiBaseUrl, config.getId())
                : ssoCallbackUrl(apiBaseUrl, config.getId());
    }

    /**
     * The callback of the configuration {@code configId} an application's login, or one of its tenants', offers: on
     * the application's API host under {@code appApiBaseUrl}, as {@link HostLabelUtil#apiUrl(ApplicationKey, String)}
     * builds it.
     *
     * @param appApiBaseUrl the base URL every application's API host is a label under
     */
    public static String applicationCallbackUrl(ApplicationKey applicationKey, String appApiBaseUrl, String configId) {
        return HostLabelUtil.apiUrl(applicationKey, appApiBaseUrl) + APPLICATION_CALLBACK_PATH + configId;
    }
}
