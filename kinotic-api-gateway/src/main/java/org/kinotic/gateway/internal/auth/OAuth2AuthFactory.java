package org.kinotic.gateway.internal.auth;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.springframework.stereotype.Component;

/**
 * Builds a Vert.x {@link OAuth2Auth} for a given {@link OidcConfiguration} by running OIDC
 * discovery against the configured {@code authority}. This works uniformly for every
 * standards-compliant OIDC provider (Google, Azure AD, Keycloak, Salesforce, Cognito, Auth0,
 * Okta, Ping, Duende, custom) — the {@code authority} URL is the only input that varies.
 * <p>
 * Non-OIDC-discovery providers (GitHub, Apple, Facebook, LinkedIn, Microsoft-Live) use
 * hardcoded endpoints and non-standard client-auth flows. They will need dedicated handling
 * when first wired in — add it here as an explicit branch keyed off
 * {@link org.kinotic.os.api.model.iam.OidcProviderKind} at that point.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthFactory {

    private final Vertx vertx;

    /**
     * @param config       the persisted OIDC configuration (must have authority set)
     * @param clientSecret resolved client secret, or null for public-client flows
     */
    public Future<OAuth2Auth> create(OidcConfiguration config, String clientSecret) {
        if (config.getAuthority() == null || config.getAuthority().isBlank()) {
            return Future.failedFuture(new IllegalArgumentException(
                    "OidcConfiguration " + config.getId() + " has no authority; required for OIDC discovery"));
        }

        OAuth2Options options = new OAuth2Options()
                .setClientId(config.getClientId())
                .setSite(config.getAuthority());
        if (clientSecret != null) {
            options.setClientSecret(clientSecret);
        }

        return OpenIDConnectAuth.discover(vertx, options);
    }
}
