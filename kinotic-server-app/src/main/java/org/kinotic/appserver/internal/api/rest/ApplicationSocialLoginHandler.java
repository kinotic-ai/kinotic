package org.kinotic.appserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.appserver.internal.api.services.security.SocialLoginHandoff;
import org.kinotic.appserver.internal.api.services.security.SocialLoginHandoffService;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.security.ApplicationOidcConfiguration;
import org.kinotic.domain.api.model.security.ApplicationSocialOidcConfiguration;
import org.kinotic.domain.api.model.security.ClientRegistrationType;
import org.kinotic.domain.api.model.security.ManagedSignIn;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.core.api.service.SuppliesGatewayRoutes;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.CallbackResult;
import org.kinotic.domain.api.rest.support.OidcCallbackException;
import org.kinotic.domain.api.rest.support.OidcErrorCodes;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.rest.support.PendingFlow;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.HostLabelUtil;
import org.springframework.stereotype.Component;


/**
 * Sign-in to an application through one of its {@link ApplicationOidcConfiguration}s that uses Kinotic's
 * registration with the provider ({@link ClientRegistrationType#MANAGED}), the system's
 * {@link ApplicationSocialOidcConfiguration} of the configuration's provider kind. The registration is shared by
 * every application, and a provider returns the browser only to a redirect URI registered in advance, so the
 * provider returns to one host for all of them: the app server's own, {@code kinotic.domain.appApiBaseUrl}. The flow
 * therefore runs across two hosts. The application's API host starts it ({@link ApplicationLoginHandler}'s
 * start answers with the authorize URL on the shared host), the shared host runs the provider round-trip on
 * its own session, and its callback verifies the identity, finds or creates the application's user it signs in as,
 * and hands that user to the application's API host under a single-use code, where the session is established, so
 * the browser ends signed in at the host the application's UI connects to. The code completes the sign-in only in
 * the browser that started it: the application's host puts a value in that browser's session at the start, the
 * shared host carries it through its own session into the handoff, and the completion requires the browser to
 * present the same value again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationSocialLoginHandler implements SuppliesGatewayRoutes {

    private static final String CALLBACK_PATH = "/api/auth/app/login/social/callback/";
    private static final String COMPLETE_PATH = "/api/auth/app/login/social/complete/";

    // the application's configuration a flow at the shared host signs in through, held there for its callback, whose
    // path names the system registration the provider returns to
    private static final String CONFIGURATION_SESSION_KEY = "socialLoginConfiguration";

    private final ParticipantIdentityService identityService;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final SocialLoginHandoffService handoffService;
    private final ApplicationAuthEndpointSupport authEndpointSupport;
    private final KinoticDomainProperties domainProperties;

    @Override
    public void mountRoutes(Router router) {
        router.get(ApplicationAuthEndpointSupport.SOCIAL_AUTHORIZE_PATH).handler(this::handleAuthorize);
        router.get(CALLBACK_PATH + ":configId").handler(this::handleCallback);
        router.get(COMPLETE_PATH + ":code").handler(this::handleComplete);
    }

    /**
     * {@code GET /api/auth/app/login/social/authorize?organizationId&applicationId&configurationId&origin&binding[&tenantId]},
     * at the shared host — begins the provider round-trip through Kinotic's registration for a sign-in to the
     * application, or to its tenant from one of the tenant's sites or when {@code tenantId} names one, from the page at
     * {@code origin}, through the application's configuration {@code configurationId}, redirecting the browser to the
     * provider. {@code binding} is the value the application's host put in the browser's session, which the completion
     * there requires again. {@code 400} for a page that is not one of the application's UIs or a start that carries no
     * binding; a configuration the login does not offer through Kinotic's registration returns the browser to the
     * page's login with {@code config_not_found}.
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
        String binding = ctx.request().getParam("binding");
        if (binding == null || binding.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "The sign-in was not started at the application's API host");
            return;
        }
        authEndpointSupport.loginScope(applicationKey, origin, ctx.request().getParam("tenantId"))
              .compose(scope -> findManaged(configId, applicationKey)
                      .compose(signIn -> oidcFlowOrchestrator.startFlow(ctx, signIn.registration(), callbackUrl(signIn.registration().getId()),
                                                                        applicationKey, scope.tenantId(), origin)))
              .onSuccess(url -> {
                  // held through the provider round-trip for the callback to hand on with the identity; written with the
                  // flow startFlow stored, so the keys and the flow always belong to the same start
                  ctx.session().put(ApplicationAuthEndpointSupport.SOCIAL_LOGIN_BINDING_SESSION_KEY, binding);
                  ctx.session().put(CONFIGURATION_SESSION_KEY, configId);
                  ctx.response().setStatusCode(302).putHeader("Location", url).end();
              })
              .onFailure(err -> {
                  log.warn("Social login authorize failed for {}/{}: {}", HostLabelUtil.label(applicationKey), configId, err.getMessage());
                  // the flow has not started, so the page that asked is still known to send the browser back to
                  authEndpointSupport.redirectError(ctx, origin, err instanceof OidcCallbackException oce
                          ? oce.getErrorCode() : OidcErrorCodes.EXCHANGE_FAILED);
              });
    }

    // the application's configuration that signs in through Kinotic's registration, with that registration, failing
    // with CONFIG_NOT_FOUND when the application offers none by the id or the system holds no registration of its kind
    private Future<ManagedSignIn> findManaged(String configId, ApplicationKey applicationKey) {
        return oidcConfigurationService.findOfferedManaged(configId, applicationKey.organizationId(), applicationKey.applicationId())
                                       .map(signIn -> {
                                           if (signIn == null) {
                                               throw new OidcCallbackException(OidcErrorCodes.CONFIG_NOT_FOUND);
                                           }
                                           return signIn;
                                       });
    }

    /**
     * {@code GET /api/auth/app/login/social/callback/:configId}, at the shared host — the provider returns
     * here; validates the callback against the flow session, finds or creates the application's user the identity
     * signs in as through the application's configuration the flow started with, and sends the browser on to the
     * application's API host with the single-use code its sign-in is staged under. A callback that fails, or whose
     * identity signs in as no user, returns the browser to the login page of the page that started the flow with
     * the error; one no flow session answers for is refused with {@code 400}: the host serves every application,
     * so without the session there is no page to send the browser back to.
     */
    private void handleCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");
        // read before the callback consumes the flow: a failed callback still returns to the page that started it
        PendingFlow flow = oidcFlowOrchestrator.pendingFlow(ctx);
        String configId = ctx.session().remove(CONFIGURATION_SESSION_KEY);
        Future<ManagedSignIn> offered = flow != null && configId != null && flow.orgId() != null && flow.applicationId() != null
                ? oidcConfigurationService.findOfferedManaged(configId, flow.orgId(), flow.applicationId())
                : Future.succeededFuture();

        // transform rather than compose, so handleCallback consumes the flow whatever the lookup found
        offered.transform(found -> oidcFlowOrchestrator.<ApplicationSocialOidcConfiguration>handleCallback(
                       ctx, pathConfigId, callbackUrl(pathConfigId),
                       // the provider returned to the registration the flow started with, or the flow is not this one
                       (_, _, _) -> found.failed()
                               ? Future.failedFuture(found.cause())
                               : Future.succeededFuture(found.result() != null && found.result().registration().getId().equals(pathConfigId)
                                                                ? found.result().registration() : null))
                       // a callback that reached here resolved the registration, so the lookup found the configuration
                       .compose(result -> handOff(ctx, result, found.result().configuration())))
               .onFailure(ex -> {
                   log.warn("Social login callback failed for config {}: {}", pathConfigId, ex.getMessage());
                   if (flow != null && flow.origin() != null) {
                       authEndpointSupport.redirectCallbackFailure(ctx, flow.origin(), ex);
                   } else {
                       authEndpointSupport.respondError(ctx, 400, "Sign-in failed: " + authEndpointSupport.callbackErrorCode(ex));
                   }
               });
    }

    private Future<Void> handOff(RoutingContext ctx, CallbackResult<? extends OidcConfiguration> result, ApplicationOidcConfiguration configuration) {
        Future<Void> ret = Future.succeededFuture();
        if (authEndpointSupport.verifiedSubject(ctx, result) != null) {
            ApplicationKey applicationKey = new ApplicationKey(result.orgId(), result.applicationId());
            ret = authEndpointSupport.loginScope(applicationKey, result.tenantId())
                    .compose(scope -> authEndpointSupport.findOrCreateUser(scope, configuration, result.claims()))
                    .map(user -> {
                        if (user == null) {
                            authEndpointSupport.redirectError(ctx, result.origin(), OidcErrorCodes.NO_USER);
                        } else {
                            String code = handoffService.stage(new SocialLoginHandoff()
                                                                       .setOrganizationId(applicationKey.organizationId())
                                                                       .setApplicationId(applicationKey.applicationId())
                                                                       .setIdentityId(user.getId())
                                                                       .setOrigin(result.origin())
                                                                       .setBinding(ctx.session().remove(ApplicationAuthEndpointSupport.SOCIAL_LOGIN_BINDING_SESSION_KEY)));
                            ctx.response().setStatusCode(302)
                               .putHeader("Location", HostLabelUtil.apiUrl(applicationKey, domainProperties.getDomain().getAppApiBaseUrl()) + COMPLETE_PATH + code)
                               .end();
                        }
                        return null;
                    });
        }
        return ret;
    }

    /**
     * {@code GET /api/auth/app/login/social/complete/:code}, at the application's API host — consumes the
     * staged sign-in and logs its user into the application, or into the tenant the sign-in was to, bound to the
     * page that started it, as {@link ApplicationLoginHandler}'s own callback does. A code that is unknown,
     * spent, staged for another application, or presented by a browser other than the one that started the
     * sign-in sends the browser to the application's login page with {@code handoff_invalid}.
     */
    private void handleComplete(RoutingContext ctx) {
        ApplicationKey applicationKey = authEndpointSupport.applicationKey(ctx);
        SocialLoginHandoff handoff = handoffService.consume(ctx.pathParam("code"));
        String binding = ctx.session().remove(ApplicationAuthEndpointSupport.SOCIAL_LOGIN_BINDING_SESSION_KEY);
        // the code is bound to the host it was issued for and to the browser whose session the start wrote, so a
        // code carried to another application's host, or sent to another person, signs no one in
        if (handoff == null
                || !applicationKey.equals(new ApplicationKey(handoff.getOrganizationId(), handoff.getApplicationId()))
                || binding == null || !binding.equals(handoff.getBinding())) {
            authEndpointSupport.redirectError(ctx, null, OidcErrorCodes.HANDOFF_INVALID);
        } else {
            authEndpointSupport.completeLogin(ctx, handoff.getOrigin(),
                    // the callback staged a user of the sign-in's scope, so only its application is checked here, against the host
                    identityService.findById(handoff.getIdentityId())
                                   .map(identity -> identity instanceof ApplicationParticipantIdentity user
                                                    && applicationKey.equals(new ApplicationKey(user.getOrganizationId(), user.getApplicationId()))
                                           ? user : null)
                                   .map(user -> user == null ? null : AuthEndpointSupport.participantOf(user)));
        }
    }

    // the shared host is the callback's base: the one redirect URI the registration lists for every application
    private String callbackUrl(String configId) {
        return domainProperties.getDomain().getAppApiBaseUrl() + CALLBACK_PATH + configId;
    }
}
