package org.kinotic.appserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.appserver.api.config.AppServerProperties;
import org.kinotic.appserver.internal.api.services.security.SocialLoginHandoff;
import org.kinotic.appserver.internal.api.services.security.SocialLoginHandoffService;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.security.ApplicationSocialOidcConfiguration;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.CallbackResult;
import org.kinotic.domain.api.rest.support.OidcCallbackException;
import org.kinotic.domain.api.rest.support.OidcErrorCodes;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.HostLabelUtil;
import org.springframework.stereotype.Component;


/**
 * Sign-in to an application through one of the platform's social registrations
 * ({@link ApplicationSocialOidcConfiguration}). The registration is shared by every application that offers
 * it, and a provider returns the browser only to a redirect URI registered in advance, so the provider
 * returns to one host for all of them: the app server's own, {@code kinotic.appServer.apiBaseUrl}. The flow
 * therefore runs across two hosts. The application's API host starts it ({@link ApplicationLoginHandler}'s
 * start answers with the authorize URL on the shared host), the shared host runs the provider round-trip on
 * its own session, and its callback
 * verifies the identity and hands it to the application's API host under a single-use code, where the
 * session is established, so the browser ends signed in at the host the application's UI connects to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationSocialLoginHandler implements SuppliesGatewayRoutes {

    private static final String CALLBACK_PATH = "/api/auth/app/login/social/callback/";
    private static final String COMPLETE_PATH = "/api/auth/app/login/social/complete/";

    private final ParticipantIdentityService identityService;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final SocialLoginHandoffService handoffService;
    private final ApplicationAuthEndpointSupport authEndpointSupport;
    private final AppServerProperties properties;

    @Override
    public void mountRoutes(Router router) {
        router.get(ApplicationAuthEndpointSupport.SOCIAL_AUTHORIZE_PATH).handler(this::handleAuthorize);
        router.get(CALLBACK_PATH + ":configId").handler(this::handleCallback);
        router.get(COMPLETE_PATH + ":code").handler(this::handleComplete);
    }

    /**
     * {@code GET /api/auth/app/login/social/authorize?organizationId&applicationId&configurationId&origin[&tenantId]},
     * at the shared host — begins the provider round-trip for a sign-in to the application, or to its tenant
     * from one of the tenant's sites or when {@code tenantId} names one, from the page at {@code origin},
     * redirecting the browser to the provider. {@code 400} for a configuration the login does not offer, or a
     * page that is not one of the application's UIs.
     */
    private void handleAuthorize(RoutingContext ctx) {
        ApplicationKey applicationKey = new ApplicationKey(ctx.request().getParam("organizationId"),
                                                           ctx.request().getParam("applicationId"));
        String configId = ctx.request().getParam("configurationId");
        String origin = ctx.request().getParam("origin");
        // the completed sign-in is bound to this page, so only a page of the application may be named
        if (origin == null || !authEndpointSupport.isApplicationUi(applicationKey, origin)) {
            authEndpointSupport.respondError(ctx, 400, "The page is not a UI of the application");
            return;
        }

        authEndpointSupport.loginScope(applicationKey, origin, ctx.request().getParam("tenantId"))
              .compose(scope -> oidcConfigurationService.findOffered(configId, applicationKey.organizationId(),
                                                                     applicationKey.applicationId(), scope.tenantId())
                                                        .compose(config -> {
                  Future<String> ret;
                  if (config instanceof ApplicationSocialOidcConfiguration) {
                      ret = oidcFlowOrchestrator.startFlow(ctx, config, callbackUrl(configId), applicationKey, scope.tenantId(), origin);
                  } else {
                      ret = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.CONFIG_NOT_FOUND));
                  }
                  return ret;
              }))
              .onSuccess(url -> ctx.response().setStatusCode(302).putHeader("Location", url).end())
              .onFailure(err -> {
                  log.warn("Social login authorize failed for {}/{}: {}", HostLabelUtil.label(applicationKey), configId, err.getMessage());
                  // the flow has not started, so the page that asked is still known to send the browser back to
                  authEndpointSupport.redirectError(ctx, origin, err instanceof OidcCallbackException oce
                          ? oce.getErrorCode() : OidcErrorCodes.EXCHANGE_FAILED);
              });
    }

    /**
     * {@code GET /api/auth/app/login/social/callback/:configId}, at the shared host — the provider returns
     * here; validates the callback against the flow session and, for an identity that may sign in, sends
     * the browser on to the application's API host with the single-use code its sign-in is staged under.
     * A callback no flow session answers for is refused with {@code 400}: the host serves every application,
     * so without the session there is no page to send the browser back to.
     */
    private void handleCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.<OidcConfiguration>handleCallback(
                ctx, pathConfigId, callbackUrl(pathConfigId),
                (orgId, applicationId, tenantId) -> oidcConfigurationService.findOffered(pathConfigId, orgId, applicationId, tenantId))
                .onSuccess(result -> handOff(ctx, result))
                .onFailure(ex -> {
                    String code = ex instanceof OidcCallbackException oce ? oce.getErrorCode() : OidcErrorCodes.EXCHANGE_FAILED;
                    log.warn("Social login callback failed for config {}: {}", pathConfigId, ex.getMessage());
                    authEndpointSupport.respondError(ctx, 400, "Sign-in failed: " + code);
                });
    }

    private void handOff(RoutingContext ctx, CallbackResult<OidcConfiguration> result) {
        String sub = authEndpointSupport.verifiedSubject(ctx, result);
        if (sub != null) {
            ApplicationKey applicationKey = new ApplicationKey(result.orgId(), result.applicationId());
            String code = handoffService.stage(new SocialLoginHandoff()
                                                       .setOrganizationId(applicationKey.organizationId())
                                                       .setApplicationId(applicationKey.applicationId())
                                                       .setTenantId(result.tenantId())
                                                       .setConfigurationId(result.config().getId())
                                                       .setSubject(sub)
                                                       .setOrigin(result.origin()));
            ctx.response().setStatusCode(302)
               .putHeader("Location", HostLabelUtil.apiUrl(applicationKey, properties.getApiBaseUrl()) + COMPLETE_PATH + code)
               .end();
        }
    }

    /**
     * {@code GET /api/auth/app/login/social/complete/:code}, at the application's API host — consumes the
     * staged sign-in and logs its identity into the application, or into the tenant the sign-in was to, bound
     * to the page that started it, as {@link ApplicationLoginHandler}'s own callback does. A code that is unknown, spent, or staged for
     * another application sends the browser to the application's login page with {@code handoff_invalid}.
     */
    private void handleComplete(RoutingContext ctx) {
        ApplicationKey applicationKey = authEndpointSupport.applicationKey(ctx);
        SocialLoginHandoff handoff = handoffService.consume(ctx.pathParam("code"));
        // the code is bound to the host it was issued for, so a code carried to another application's host
        // cannot sign its identity in there
        if (handoff == null
                || !applicationKey.equals(new ApplicationKey(handoff.getOrganizationId(), handoff.getApplicationId()))) {
            authEndpointSupport.redirectError(ctx, null, OidcErrorCodes.HANDOFF_INVALID);
        } else {
            LoginScope scope = new LoginScope(applicationKey, handoff.getTenantId());
            authEndpointSupport.completeLogin(ctx, handoff.getOrigin(),
                    identityService.findByOidcIdentity(handoff.getConfigurationId(), handoff.getSubject(),
                                                       applicationKey.organizationId(), applicationKey.applicationId())
                                   .map(scope::memberOf)
                                   .map(user -> user == null ? null : AuthEndpointSupport.participantOf(user)));
        }
    }

    // the shared host is the callback's base: the one redirect URI the registration lists for every application
    private String callbackUrl(String configId) {
        return properties.getApiBaseUrl() + CALLBACK_PATH + configId;
    }
}
