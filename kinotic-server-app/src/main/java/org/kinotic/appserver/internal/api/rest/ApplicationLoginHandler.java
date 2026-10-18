package org.kinotic.appserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.appserver.api.config.AppServerProperties;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.ApplicationSocialOidcConfiguration;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.kinotic.domain.api.rest.support.CallbackResult;
import org.kinotic.domain.api.rest.support.OidcCallbackException;
import org.kinotic.domain.api.rest.support.OidcErrorCodes;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.api.utils.HostLabelUtil;
import org.springframework.stereotype.Component;

/**
 * Login routes for end-users of an application built on Kinotic, served at the application's own API host.
 * Distinct from the org-login handler: the user is logging into an application's own user base (an
 * APP-scope {@link UserParticipantIdentity} — {@code organizationId} + {@code applicationId} both set),
 * not into the platform-managed org admin surface. The application is the one whose API host the request
 * was addressed to, a login is made only from a page that is one of that application's UIs, and a login
 * from one of a tenant's sites, or one naming a tenant, is to that tenant: it admits the tenant's users and
 * offers the tenant's own providers as well as the application's (see
 * {@link ApplicationAuthEndpointSupport#loginScope}). A page offers the methods the login supports — email
 * and password, and the configurations {@code GET /api/auth/app/login/providers} lists — without asking
 * about an email first, so no endpoint tells whether an email has an account. An OIDC flow started here
 * returns to this handler's own callback at the application's API host; a sign-in through one of the
 * platform's social registrations, whose callback every application shares, runs through
 * {@link ApplicationSocialLoginHandler}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationLoginHandler implements SuppliesGatewayRoutes {

    private final ParticipantIdentityService identityService;
    private final OidcConfigurationService oidcConfigurationService;
    private final ApplicationRepository applicationRepository;
    private final LocalAuthenticationService localAuthenticationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final ApplicationAuthEndpointSupport authEndpointSupport;
    private final AppServerProperties properties;

    @Override
    public void mountRoutes(Router router) {
        router.get("/api/auth/app/login/providers").handler(this::handleProviders);
        router.post("/api/auth/app/login/oidc/start/:configId").handler(this::handleStart);
        router.post("/api/auth/app/login").handler(this::handleLogin);
        router.get("/api/auth/app/login/oidc/callback/:configId").handler(this::handleCallback);
    }

    /**
     * {@code GET /api/auth/app/login/providers[?tenantId]} — lists the enabled configurations the application's
     * login offers, or a tenant's login from one of the tenant's sites or when {@code tenantId} names one.
     */
    private void handleProviders(RoutingContext ctx) {
        authEndpointSupport.loginScope(ctx, ctx.request().getParam("tenantId"))
              .compose(scope -> oidcConfigurationService.findEnabledForScope(scope.application().organizationId(),
                                                                               scope.application().applicationId(),
                                                                               scope.tenantId()))
              .onSuccess(configs -> authEndpointSupport.respondProvidersList(ctx, configs))
              .onFailure(err -> fail(ctx, err, "Failed to list providers"));
    }

    /**
     * {@code POST /api/auth/app/login/oidc/start/:configId[?tenantId]} — begins a sign-in through a configuration
     * the login offers, answering {@code {redirect}} with the URL the page sends the browser to: the provider's
     * authorization URL for a configuration of the organization, whose flow returns to this handler's callback,
     * or the shared host's authorize URL for one of the platform's social registrations (see
     * {@link ApplicationSocialLoginHandler}). {@code 400} for a configuration the login does not offer;
     * {@code 403} from a page that is not one of the application's UIs.
     */
    private void handleStart(RoutingContext ctx) {
        String configId = ctx.pathParam("configId");
        String origin = SessionBinding.origin(ctx);
        if (origin == null) {
            authEndpointSupport.respondError(ctx, 400, "A sign-in is started by a page");
            return;
        }

        authEndpointSupport.loginScope(ctx, ctx.request().getParam("tenantId"))
              .compose(scope -> oidcConfigurationService.findOffered(configId, scope.application().organizationId(),
                                                                     scope.application().applicationId(), scope.tenantId())
                                                        .compose(config -> {
                  Future<String> ret;
                  switch (config) {
                      case null -> ret = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.CONFIG_NOT_FOUND));
                      // the registration is shared, so its provider returns to the shared host, not to this one
                      case ApplicationSocialOidcConfiguration _ ->
                              ret = Future.succeededFuture(authEndpointSupport.socialAuthorizeUrl(scope, configId, origin));
                      default -> ret = oidcFlowOrchestrator.startFlow(ctx, config, callbackUrl(scope.application(), configId),
                                                                      scope.application(), scope.tenantId(), origin);
                  }
                  return ret;
              }))
              .onSuccess(url -> ctx.response().putHeader("Content-Type", "application/json")
                                   .end(new JsonObject().put("redirect", url).encode()))
              .onFailure(err -> {
                  if (err instanceof OidcCallbackException) {
                      authEndpointSupport.respondError(ctx, 400, "The login does not offer " + configId);
                  } else {
                      fail(ctx, err, "Failed to start sign-in");
                  }
              });
    }

    /**
     * {@code POST /api/auth/app/login {email, password[, tenantId]}} — local password auth, scoped to the
     * application, or to the tenant from one of the tenant's sites or when {@code tenantId} names one, so a stray
     * cross-scope match can't authenticate here. Refused with {@code 403} from a page that is not one of the
     * application's UIs.
     */
    private void handleLogin(RoutingContext ctx) {
        String tenantId = authEndpointSupport.readJsonBody(ctx).getString("tenantId");
        authEndpointSupport.loginScope(ctx, tenantId)
              .onSuccess(scope -> authEndpointSupport.handlePasswordLogin(ctx,
                      (email, password) -> localAuthenticationService.authenticateLocal(
                              email, password, scope.application().organizationId(), scope.application().applicationId())
                                                                       .map(user -> scope.memberOf(user) == null ? null : DomainUtil.createParticipant(user))))
              .onFailure(err -> fail(ctx, err, "Failed to sign in"));
    }

    /**
     * {@code GET /api/auth/app/login/oidc/callback/:configId} — the IdP returns here; validates the callback and
     * logs the user into the scope the flow started for. An identity no user is bound to becomes a user on its
     * first sign-in when the configuration creates users on sign-in: a user of the tenant through the tenant's
     * own configuration, or a user of the application, with a tenant of its own, through the application's
     * configuration while the platform manages the application's tenants, since under tenant administrators a
     * user is created by an administrator or an invitation and is never without a tenant.
     */
    private void handleCallback(RoutingContext ctx) {
        ApplicationKey applicationKey = authEndpointSupport.applicationKey(ctx);
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.<OidcConfiguration>handleCallback(
                ctx, pathConfigId, callbackUrl(applicationKey, pathConfigId),
                (_, _, tenantId) -> oidcConfigurationService.findOffered(pathConfigId, applicationKey.organizationId(),
                                                                         applicationKey.applicationId(), tenantId))
                .onSuccess(result -> completeAppLogin(ctx, result, new LoginScope(applicationKey, result.tenantId())))
                .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private void completeAppLogin(RoutingContext ctx,
                                  CallbackResult<OidcConfiguration> result,
                                  LoginScope scope) {
        authEndpointSupport.completeOidcLogin(ctx, result,
                sub -> identityService.findByOidcIdentity(result.config().getId(), sub, scope.application().organizationId(),
                                                         scope.application().applicationId())
                                      .map(scope::memberOf),
                () -> newUserOf(scope, result.config()));
    }

    // the unsaved user an unknown identity becomes, or null when the scope admits no new user: a tenant admits one
    // through its own configuration only, and an application only while the platform assigns the user's tenant
    private Future<UserParticipantIdentity> newUserOf(LoginScope scope, OidcConfiguration config) {
        ApplicationKey applicationKey = scope.application();
        Future<UserParticipantIdentity> ret;
        if (scope.tenantId() != null) {
            ret = Future.succeededFuture(config instanceof TenantOidcConfiguration ? newUser(applicationKey, scope.tenantId()) : null);
        } else {
            ret = applicationRepository.findById(applicationKey.applicationId(), applicationKey.organizationId())
                                       .map(application -> application != null && application.getTenantManagement() == TenantManagement.PLATFORM
                                               ? newUser(applicationKey, null) : null);
        }
        return ret;
    }

    private static UserParticipantIdentity newUser(ApplicationKey applicationKey, String tenantId) {
        UserParticipantIdentity ret = new UserParticipantIdentity();
        ret.setOrganizationId(applicationKey.organizationId())
           .setApplicationId(applicationKey.applicationId())
           .setTenantId(tenantId);
        return ret;
    }

    // a failed scope is answered with the status it names, which the router's failure handler renders
    private void fail(RoutingContext ctx, Throwable err, String message) {
        if (err instanceof io.vertx.ext.web.handler.HttpException || err instanceof org.kinotic.core.api.exceptions.AuthorizationException) {
            ctx.fail(err);
        } else {
            log.warn("{} at {}: {}", message, ctx.request().authority(), err.getMessage());
            authEndpointSupport.respondError(ctx, 500, message);
        }
    }

    // the application's API host is the callback's base, so the IdP returns the browser to the host it left
    private String callbackUrl(ApplicationKey applicationKey, String configId) {
        return HostLabelUtil.apiUrl(applicationKey, properties.getApiBaseUrl()) + "/api/auth/app/login/oidc/callback/" + configId;
    }
}
