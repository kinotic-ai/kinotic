package org.kinotic.appserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.kinotic.core.api.security.SecurityExceptionFactory;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.appserver.api.config.AppServerProperties;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.rest.support.OAuth2Util;
import org.kinotic.domain.api.utils.HostLabelUtil;
import org.kinotic.domain.api.rest.support.CallbackResult;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.model.security.AuthType;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * Login routes for end-users of an application built on Kinotic, served at the application's own API host.
 * Distinct from the org-login handler: the user is logging into an application's own user base (an
 * APP-scope {@link UserParticipantIdentity} — {@code organizationId} + {@code applicationId} both set),
 * not into the platform-managed org admin surface. The application is the one whose API host the request
 * was addressed to (see {@link ApplicationAuthEndpointSupport#applicationKey}), a login is made only from a page that is one of
 * that application's UIs, and OIDC flows started here return to this handler's own callback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationLoginHandler implements SuppliesGatewayRoutes {

    private final ParticipantIdentityService identityService;
    private final OidcConfigurationService oidcConfigurationService;
    private final LocalAuthenticationService localAuthenticationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final ApplicationAuthEndpointSupport authEndpointSupport;
    private final AppServerProperties properties;
    private final SecurityExceptionFactory securityExceptions;

    @Override
    public void mountRoutes(Router router) {
        router.get("/api/auth/app/login/providers").handler(this::handleProviders);
        router.post("/api/auth/app/login/lookup").handler(this::handleLookup);
        router.post("/api/auth/app/login").handler(this::handleLogin);
        router.get("/api/auth/app/login/oidc/callback/:configId").handler(this::handleCallback);
    }

    /**
     * {@code GET /api/auth/app/login/providers} — lists the enabled
     * {@link OidcConfiguration} rows the app references via
     * {@code Application.oidcConfigurationIds}.
     */
    private void handleProviders(RoutingContext ctx) {
        ApplicationKey applicationKey = authEndpointSupport.applicationKey(ctx);
        oidcConfigurationService.findEnabledForScope(applicationKey.organizationId(), applicationKey.applicationId())
              .onSuccess(configs -> authEndpointSupport.respondProvidersList(ctx, configs))
              .onFailure(err -> {
                  log.warn("Failed to list app providers for {}: {}", HostLabelUtil.label(applicationKey), err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Failed to list providers");
              });
    }

    /**
     * {@code POST /api/auth/app/login/lookup {email}} or {@code {tenantId}} — the SSO/password decision:
     * {@code {type:"sso", redirect:"…"}} for a user who signs in with an identity provider that is live, or for
     * a tenant that signs its users in with one of its own, otherwise {@code {type:"password"}}. Refused with
     * {@code 403} from a page that is not one of the application's UIs.
     */
    private void handleLookup(RoutingContext ctx) {
        ApplicationKey applicationKey = requireApplicationUi(ctx);
        JsonObject body = authEndpointSupport.readJsonBody(ctx);
        String email = body.getString("email");
        String tenantId = body.getString("tenantId");
        Future<Void> answered;
        if (tenantId != null && !tenantId.isBlank()) {
            // a tenant's id selects its own identity provider before any user is known, as a tenant's login page does
            answered = oidcConfigurationService.findTenantLoginConfig(applicationKey.organizationId(), applicationKey.applicationId(), tenantId)
                                               .compose(config -> config == null
                                                       ? authEndpointSupport.respondPasswordPath(ctx)
                                                       : startSso(ctx, applicationKey, config));
        } else if (email == null || email.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "email or tenantId is required");
            return;
        } else {
            answered = identityService.findByEmail(email, applicationKey.organizationId(), applicationKey.applicationId())
                                      .compose(user -> resolveSsoOrPassword(ctx, applicationKey, user));
        }
        answered.onFailure(err -> {
            log.warn("App login lookup failed for {}: {}", HostLabelUtil.label(applicationKey), err.getMessage());
            authEndpointSupport.respondError(ctx, 500, "Lookup failed");
        });
    }

    private Future<Void> resolveSsoOrPassword(RoutingContext ctx, ApplicationKey applicationKey, UserParticipantIdentity user) {
        if (user == null
                || user.getAuthType() != AuthType.OIDC
                || user.getOidcConfigId() == null) {
            return authEndpointSupport.respondPasswordPath(ctx);
        }
        String configId = user.getOidcConfigId();
        return oidcConfigurationService.findById(configId, applicationKey.organizationId())
                     .compose(match -> match == null || !match.isEnabled()
                             ? authEndpointSupport.respondPasswordPath(ctx)
                             : startSso(ctx, applicationKey, match));
    }

    private Future<Void> startSso(RoutingContext ctx, ApplicationKey applicationKey, OidcConfiguration config) {
        return oidcFlowOrchestrator.startFlow(ctx, config, callbackUrl(applicationKey, config.getId()), null)
                                   .compose(url -> authEndpointSupport.respondSsoRedirect(ctx, url));
    }

    /**
     * {@code POST /api/auth/app/login {email, password}} — local password auth,
     * scoped to the application so a stray cross-scope match can't authenticate here. Refused
     * with {@code 403} from a page that is not one of the application's UIs.
     */
    private void handleLogin(RoutingContext ctx) {
        ApplicationKey applicationKey = requireApplicationUi(ctx);
        authEndpointSupport.handlePasswordLogin(ctx,
                (email, password) -> localAuthenticationService.authenticateLocal(
                        email, password, applicationKey.organizationId(), applicationKey.applicationId()));
    }

    /**
     * {@code GET /api/auth/app/login/oidc/callback/:configId} — the IdP returns here; validates the callback
     * and logs the user into the application. A user the provider signs in for the first time through a
     * tenant's own configuration is created in that tenant. The pre-auth flow has no participant bound, so the
     * config lookup is scoped by the application's organization.
     */
    private void handleCallback(RoutingContext ctx) {
        ApplicationKey applicationKey = authEndpointSupport.applicationKey(ctx);
        String pathConfigId = ctx.pathParam("configId");
        oidcFlowOrchestrator.<OidcConfiguration>handleCallback(
                ctx, pathConfigId, callbackUrl(applicationKey, pathConfigId),
                _ -> oidcConfigurationService.findById(pathConfigId, applicationKey.organizationId()))
                .onSuccess(result -> completeAppLogin(ctx, result))
                .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private void completeAppLogin(RoutingContext ctx, CallbackResult<OidcConfiguration> result) {
        Map<String, Object> claims = result.claims();
        authEndpointSupport.completeOidcLogin(ctx, result,
                sub -> identityService.findOrCreateSsoUser(result.config(), sub,
                                                           OAuth2Util.stringClaim(claims, "email"),
                                                           OAuth2Util.firstPresent(claims, "name", "preferred_username", "email")));
    }

    /**
     * The application the request was addressed to. Fails the request with {@code 403} when the
     * page that sent it is not one of the application's UIs.
     */
    private ApplicationKey requireApplicationUi(RoutingContext ctx) {
        ApplicationKey applicationKey = authEndpointSupport.applicationKey(ctx);
        String origin = SessionBinding.origin(ctx);
        // a login authenticates the page that made it, so a login made from another application's UI would
        // hand that UI this application's user; a request naming no page comes from a client outside a browser
        if (origin != null && !isApplicationUi(applicationKey, origin)) {
            throw securityExceptions.notAuthorized("The page {} is not a UI of application {}", origin, applicationKey);
        }
        return applicationKey;
    }

    private boolean isApplicationUi(ApplicationKey applicationKey, String origin) {
        String host = URI.create(origin).getHost();
        Pattern localUiOrigins = properties.getLocalUiOriginPattern();
        // the CORS pattern holds sites to the sites domain, so a site is named by the first label of its host
        return (host != null && HostLabelUtil.isSiteLabel(applicationKey, StringUtils.substringBefore(host, ".")))
                || (localUiOrigins != null && localUiOrigins.matcher(origin).matches());
    }

    // the application's API host is the callback's base, so the IdP returns the browser to the host it left
    private String callbackUrl(ApplicationKey applicationKey, String configId) {
        return HostLabelUtil.apiUrl(applicationKey, properties.getApiBaseUrl()) + "/api/auth/app/login/oidc/callback/" + configId;
    }
}
