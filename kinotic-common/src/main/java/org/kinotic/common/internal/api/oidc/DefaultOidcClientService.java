package org.kinotic.common.internal.api.oidc;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.kinotic.common.api.oidc.OidcClient;
import org.kinotic.common.api.oidc.OidcClientService;
import org.kinotic.common.api.oidc.OidcClientSettings;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultOidcClientService implements OidcClientService {

    // github.com's token endpoint answers with a ~9 KB Content-Security-Policy header, past Vert.x's 8 KB default
    private static final int MAX_HEADER_SIZE = 32 * 1024;
    // some providers (github.com, api.github.com) reject requests that carry no User-Agent
    private static final String USER_AGENT = "kinotic-platform";

    private final Vertx vertx;
    private WebClient webClient;

    @PostConstruct
    public void start() {
        // an identity endpoint answers with the profile itself; a redirect is not followed to wherever it points
        this.webClient = WebClient.create(vertx, new WebClientOptions().setFollowRedirects(false)
                                                                       .setMaxHeaderSize(MAX_HEADER_SIZE)
                                                                       .setUserAgent(USER_AGENT));
    }

    @PreDestroy
    public void stop() {
        if (webClient != null) {
            webClient.close();
        }
    }

    @Override
    public Future<OidcClient> createClient(OidcClientSettings settings) {
        Future<OidcClient> ret;
        OAuth2Options options = new OAuth2Options()
                .setClientId(settings.getClientId())
                .setSite(settings.getAuthority())
                .setHttpClientOptions(new HttpClientOptions().setMaxHeaderSize(MAX_HEADER_SIZE));
        if (settings.getClientSecret() != null) {
            options.setClientSecret(settings.getClientSecret());
        }
        if (settings.getAuthorizationUri() != null || settings.getTokenUri() != null) {
            if (settings.getAuthorizationUri() == null || settings.getTokenUri() == null) {
                ret = Future.failedFuture(new IllegalArgumentException(
                        "authorizationUri and tokenUri must both be set to bypass OIDC discovery"));
            } else {
                // Vert.x uses an absolute URL in a *Path option as-is; OAuth2API prepends
                // the site only when the value starts with '/'.
                options.setAuthorizationPath(settings.getAuthorizationUri())
                       .setTokenPath(settings.getTokenUri())
                       .setHeaders(new JsonObject().put("User-Agent", USER_AGENT));
                ret = Future.succeededFuture(new DefaultOidcClient(OAuth2Auth.create(vertx, options), webClient, settings));
            }
        } else if (StringUtils.isBlank(settings.getAuthority())) {
            ret = Future.failedFuture(new IllegalArgumentException("authority must be set for OIDC discovery"));
        } else {
            ret = discover(options, settings);
        }
        return ret;
    }

    private Future<OidcClient> discover(OAuth2Options options, OidcClientSettings settings) {
        // Microsoft /common returns a discovery doc whose `issuer` is the literal template
        // "https://login.microsoftonline.com/{tenantid}/v2.0" — Vert.x's strict
        // {site == issuer} comparison fails. DefaultOidcClient checks the issuer itself.
        options.setValidateIssuer(false);
        return OpenIDConnectAuth.discover(vertx, options)
                                .map(oauth2 -> {
                                    if (options.getJWTOptions() != null) {
                                        // Discovery mutates JWTOptions.issuer with the {tenantid}-templated string,
                                        // which fails every per-JWT validation because the real JWT carries the
                                        // substituted tid; DefaultOidcClient re-validates the issuer from the signed tid
                                        String jwtIssuer = options.getJWTOptions().getIssuer();
                                        if (jwtIssuer != null && jwtIssuer.contains("{tenantid}")) {
                                            options.getJWTOptions().setIssuer(null);
                                        }
                                        // validated during the token exchange too, so coverage holds across Vert.x
                                        // version drift
                                        if (StringUtils.isNotBlank(settings.getAudience())) {
                                            options.getJWTOptions().addAudience(settings.getAudience());
                                        }
                                    }
                                    return new DefaultOidcClient(oauth2, webClient, settings);
                                });
    }

}
