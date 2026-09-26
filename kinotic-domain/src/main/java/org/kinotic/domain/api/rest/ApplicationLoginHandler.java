package org.kinotic.domain.api.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.AppHost;
import org.kinotic.domain.internal.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.internal.api.rest.support.CallbackResult;
import org.kinotic.domain.internal.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.model.security.AuthType;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.internal.api.repositories.OidcConfigurationRepository;
import org.kinotic.domain.api.services.security.OidcConfigurationService;

import java.net.URI;
import java.util.regex.Pattern;


/**
 * Login routes for end-users of an application built on Kinotic, served at the application's own API host.
 * Distinct from the org-login handler: the user is logging into an application's own user base (an
 * APP-scope {@link UserParticipantIdentity} — {@code organizationId} + {@code applicationId} both set),
 * not into the platform-managed org admin surface. The application is the one whose API host the request
 * was addressed to (see {@link AppServerSurface#appHost}), a login is made only from a page that is one of
 * that application's UIs, and OIDC flows started here return to this handler's own callback.
 */
@Slf4j
@RequiredArgsConstructor
public class ApplicationLoginHandler implements SuppliesGatewayRoutes {

    private final ParticipantIdentityService identityService;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcConfigurationRepository oidcConfigurationRepository;
    private final LocalAuthenticationService localAuthenticationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final AuthEndpointSupport authEndpointSupport;
    private final AppServerSurface appServerSurface;
    private final KinoticDomainProperties domainProperties;

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
        AppHost appHost = appServerSurface.appHost(ctx);
        oidcConfigurationService.findEnabledForScope(appHost.organizationId(), appHost.applicationId())
              .onSuccess(configs -> authEndpointSupport.respondProvidersList(ctx, configs))
              .onFailure(err -> {
                  log.warn("Failed to list app providers for {}: {}", appHost.label(), err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Failed to list providers");
              });
    }

    /**
     * {@code POST /api/auth/app/login/lookup {email}} — email-first SSO/password
     * decision: {@code {type:"sso", redirect:"…"}} when the user is OIDC with a live
     * config, otherwise {@code {type:"password"}}. Refused with {@code 403} from a page that is
     * not one of the application's UIs.
     */
    private void handleLookup(RoutingContext ctx) {
        AppHost appHost = requireApplicationUi(ctx);
        JsonObject body = authEndpointSupport.readJsonBody(ctx);
        String email = body.getString("email");
        if (email == null || email.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "email is required");
            return;
        }

        identityService.findByEmail(email, appHost.organizationId(), appHost.applicationId())
              .compose(user -> resolveSsoOrPassword(ctx, appHost, user))
              .onFailure(err -> {
                  log.warn("App login lookup failed for {}/{}: {}", appHost.label(), email, err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Lookup failed");
              });
    }

    private Future<Void> resolveSsoOrPassword(RoutingContext ctx, AppHost appHost, UserParticipantIdentity user) {
        if (user == null
                || user.getAuthType() != AuthType.OIDC
                || user.getOidcConfigId() == null) {
            return authEndpointSupport.respondPasswordPath(ctx);
        }

        String configId = user.getOidcConfigId();
        return oidcConfigurationRepository.findById(configId, appHost.organizationId())
                     .compose(match -> {
                         if (match == null || !match.isEnabled()) {
                             return authEndpointSupport.respondPasswordPath(ctx);
                         }
                         return oidcFlowOrchestrator.startFlow(ctx, match, callbackUrl(ctx, match.getId()), null)
                                 .compose(url -> authEndpointSupport.respondSsoRedirect(ctx, url));
                     });
    }

    /**
     * {@code POST /api/auth/app/login {email, password}} — local password auth,
     * scoped to the application so a stray cross-scope match can't authenticate here. Refused
     * with {@code 403} from a page that is not one of the application's UIs.
     */
    private void handleLogin(RoutingContext ctx) {
        AppHost appHost = requireApplicationUi(ctx);
        authEndpointSupport.handlePasswordLogin(ctx,
                (email, password) -> localAuthenticationService.authenticateLocal(
                        email, password, appHost.organizationId(), appHost.applicationId()));
    }

    /**
     * {@code GET /api/auth/app/login/oidc/callback/:configId} — the IdP returns here;
     * validates the callback and logs the user into the application. The pre-auth flow has
     * no participant bound, so the config lookup is scoped by the application's organization.
     */
    private void handleCallback(RoutingContext ctx) {
        AppHost appHost = appServerSurface.appHost(ctx);
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.<OidcConfiguration>handleCallback(
                ctx, pathConfigId, callbackUrl(ctx, pathConfigId),
                _ -> oidcConfigurationRepository.findById(pathConfigId, appHost.organizationId()))
                .onSuccess(result -> completeAppLogin(ctx, result, appHost))
                .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private void completeAppLogin(RoutingContext ctx,
                                  CallbackResult<OidcConfiguration> result,
                                  AppHost appHost) {
        authEndpointSupport.completeOidcLogin(ctx, result,
                sub -> identityService.findByOidcIdentity(sub, result.config().getId(),
                                                         appHost.organizationId(), appHost.applicationId()));
    }

    /**
     * The application the request was addressed to. Fails the request with {@code 403} when the
     * page that sent it is not one of the application's UIs.
     */
    private AppHost requireApplicationUi(RoutingContext ctx) {
        AppHost appHost = appServerSurface.appHost(ctx);
        String origin = SessionBinding.origin(ctx);
        // a login authenticates the page that made it, so a login made from another application's UI would
        // hand that UI this application's user; a request naming no page comes from a client outside a browser
        if (origin != null && !isApplicationUi(appHost, origin)) {
            throw new AuthorizationException("The page is not a UI of this application");
        }
        return appHost;
    }

    private boolean isApplicationUi(AppHost appHost, String origin) {
        String host = URI.create(origin).getHost();
        Pattern localUiOrigins = domainProperties.getDomain().getLocalAppUiOriginPattern();
        // the CORS pattern holds sites to the sites domain, so a site is named by the first label of its host
        return (host != null && appHost.isSiteLabel(StringUtils.substringBefore(host, ".")))
                || (localUiOrigins != null && localUiOrigins.matcher(origin).matches());
    }

    private String callbackUrl(RoutingContext ctx, String configId) {
        return appServerSurface.apiBaseUrl(ctx) + "/api/auth/app/login/oidc/callback/" + configId;
    }
}
