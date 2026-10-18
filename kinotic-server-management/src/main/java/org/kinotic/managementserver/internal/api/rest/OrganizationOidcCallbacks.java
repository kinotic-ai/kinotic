package org.kinotic.managementserver.internal.api.rest;

import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;

/**
 * The one callback URL each configuration an organization's login offers is registered with at its provider:
 * the social callback for the platform's org sign-up registrations and the SSO callback for an organization's
 * own configuration. Every flow through the configuration, whatever its purpose, starts with this URL and
 * returns to {@link OrganizationOidcCallbackHandler}.
 */
final class OrganizationOidcCallbacks {

    static final String SOCIAL_CALLBACK_PATH = "/api/auth/org/login/social/callback/";
    static final String SSO_CALLBACK_PATH = "/api/auth/org/login/sso/callback/";

    private OrganizationOidcCallbacks() {}

    static String socialCallbackUrl(String apiBaseUrl, String configId) {
        return apiBaseUrl + SOCIAL_CALLBACK_PATH + configId;
    }

    static String ssoCallbackUrl(String apiBaseUrl, String configId) {
        return apiBaseUrl + SSO_CALLBACK_PATH + configId;
    }

    static String callbackUrl(String apiBaseUrl, OidcConfiguration config) {
        return config instanceof OrgSignupOidcConfiguration
                ? socialCallbackUrl(apiBaseUrl, config.getId())
                : ssoCallbackUrl(apiBaseUrl, config.getId());
    }
}
