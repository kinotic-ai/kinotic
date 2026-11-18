package org.kinotic.systemserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.common.api.oidc.OAuth2Util;
import org.kinotic.common.api.oidc.OidcCallbackException;
import org.kinotic.common.api.oidc.OidcErrorCodes;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.core.api.service.SuppliesGatewayRoutes;
import org.kinotic.domain.api.exceptions.UserDisabledException;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.SystemOidcConfiguration;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.CallbackResult;
import org.kinotic.domain.api.rest.support.OidcCallbacks;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.rest.support.OidcFlowPurpose;
import org.kinotic.domain.api.rest.support.PendingFlow;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.springframework.stereotype.Component;

/**
 * Login routes for system users ({@code SystemParticipantIdentity}), with the methods the deployment's operators
 * configured for the system console's login: an email and a password, and their own providers
 * ({@link SystemOidcConfiguration}). A provider signs in the system users an operator added, and a signed-in system
 * user connects one to itself as a further sign-in method. System users have no sign-up. On success the browser
 * session is established; the STOMP WebSocket handshake then authenticates from that session cookie.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemLoginHandler implements SuppliesGatewayRoutes {

    /** The console page a system user's sign-in methods are connected from. */
    private static final String PROFILE_PATH = "/account/profile";

    private final AuthEndpointSupport authEndpointSupport;
    private final LocalAuthenticationService localAuthenticationService;
    private final ParticipantIdentityService identityService;
    private final OidcConfigurationService oidcConfigurationService;
    private final PasswordConfigurationService passwordConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final KinoticManagementApiProperties managementApiProperties;

    @Override
    public void mountRoutes(Router router) {
        router.post("/api/auth/system/login").handler(this::handleLogin);
        router.get("/api/auth/system/login/providers").handler(this::handleProviders);
        router.post("/api/auth/system/login/start/:configId").handler(this::handleStart);
        router.post("/api/auth/system/methods/start/:configId").handler(this::handleConnectStart);
        router.get(OidcCallbacks.SYSTEM_CALLBACK_PATH + ":configId").handler(this::handleCallback);
    }

    /**
     * {@code POST /api/auth/system/login {email, password}} — the system user's password, so an organization's or an
     * application's user with the same email cannot authenticate here. Generic {@code 401} on any failure, which
     * every password is while the system console's login takes none.
     */
    private void handleLogin(RoutingContext ctx) {
        authEndpointSupport.handlePasswordLogin(ctx,
                (email, password) -> localAuthenticationService.authenticateSystemUser(email, password)
                                                               .compose(user -> user == null
                                                                       ? Future.succeededFuture()
                                                                       : identityService.resolveParticipant(user, null, null)));
    }

    /** {@code GET /api/auth/system/login/providers} — what the login page offers, as {@code [{id, type, name, provider}]}. */
    private void handleProviders(RoutingContext ctx) {
        Future.all(passwordConfigurationService.findOffered(DomainUtil.SYSTEM_ID, null, null),
                   oidcConfigurationService.findEnabledForScope(DomainUtil.SYSTEM_ID, null, null))
              .onSuccess(offered -> ctx.json(authEndpointSupport.providersJson(offered.resultAt(1), offered.resultAt(0))))
              .onFailure(ctx::fail);
    }

    /**
     * {@code POST /api/auth/system/login/start/:configId} — sends the browser to the provider of the configuration
     * {@code configId}. A {@code referer} query parameter naming a path within the console is where the browser
     * lands once signed in.
     */
    private void handleStart(RoutingContext ctx) {
        String configId = ctx.pathParam("configId");
        offered(configId)
                .onSuccess(config -> {
                    if (config == null) {
                        authEndpointSupport.redirectError(ctx, SessionBinding.origin(ctx), OidcErrorCodes.CONFIG_NOT_FOUND);
                    } else {
                        authEndpointSupport.redirectToIdp(ctx, config, callbackUrl(configId), DomainUtil.SYSTEM_ID, SessionBinding.origin(ctx));
                    }
                })
                .onFailure(ctx::fail);
    }

    /**
     * {@code POST /api/auth/system/methods/start/:configId} — begins the round-trip to the provider of the
     * configuration {@code configId} for the signed-in system user, who connects the identity it asserts as a
     * further sign-in method. {@code 400} for a configuration the login does not offer, {@code 401} without a
     * signed-in user.
     */
    private void handleConnectStart(RoutingContext ctx) {
        Participant user = authEndpointSupport.requireSessionUser(ctx);
        String configId = ctx.pathParam("configId");
        offered(configId)
                .compose(config -> config == null
                        ? Future.failedFuture(new IllegalArgumentException("Unknown or disabled configuration: " + configId))
                        : oidcFlowOrchestrator.startConnect(ctx, config, callbackUrl(configId), DomainUtil.SYSTEM_ID, user.getId()))
                .onSuccess(url -> ctx.response().setStatusCode(302).putHeader("Location", url).end())
                .onFailure(err -> {
                    if (err instanceof IllegalArgumentException) {
                        authEndpointSupport.respondError(ctx, 400, err.getMessage());
                    } else {
                        log.error("Connecting configuration {} failed to start", configId, err);
                        authEndpointSupport.respondError(ctx, 500, "Provider initialization failed");
                    }
                });
    }

    /**
     * The provider's callback for the configuration {@code :configId}. A sign-in signs in the system user the
     * identity is bound to, or the one an operator added under the email the provider verified, whom the identity
     * is then bound to. A connect flow binds the identity to the system user that started it.
     */
    private void handleCallback(RoutingContext ctx) {
        String configId = ctx.pathParam("configId");
        // read before the callback is handled, which consumes the flow: a refused callback returns to where it started
        PendingFlow pending = oidcFlowOrchestrator.pendingFlow(ctx);
        oidcFlowOrchestrator.handleCallback(ctx, configId, callbackUrl(configId),
                                            (orgId, applicationId, tenantId) -> offered(configId))
                            .onSuccess(result -> {
                                if (result.purpose() == OidcFlowPurpose.CONNECT) {
                                    authEndpointSupport.completeConnect(ctx, result, PROFILE_PATH);
                                } else {
                                    authEndpointSupport.completeLogin(ctx, result.origin(), result.config().getProvider(), signIn(result));
                                }
                            })
                            .onFailure(err -> {
                                if (pending != null && pending.purpose() == OidcFlowPurpose.CONNECT) {
                                    authEndpointSupport.redirectToUi(ctx, PROFILE_PATH + "?error=" + authEndpointSupport.callbackErrorCode(err));
                                } else {
                                    authEndpointSupport.redirectCallbackFailure(ctx, pending != null ? pending.origin() : null, err);
                                }
                            });
    }

    private Future<Participant> signIn(CallbackResult<OidcConfiguration> result) {
        Future<Participant> ret;
        try {
            String sub = authEndpointSupport.requireVerifiedSubject(result);
            String configId = result.config().getId();
            String email = OAuth2Util.stringClaim(result.claims(), "email");
            ret = identityService.findSystemUserByOidcIdentity(configId, sub)
                                 .compose(bound -> {
                                     Future<Participant> signedIn;
                                     if (bound != null && !bound.isEnabled()) {
                                         signedIn = Future.failedFuture(new UserDisabledException("This user cannot sign in."));
                                     } else if (bound != null) {
                                         signedIn = identityService.resolveParticipant(bound, null, null);
                                     } else if (email == null) {
                                         signedIn = Future.succeededFuture();
                                     } else {
                                         signedIn = identityService.claimProvisionedSystemUser(configId, sub, email);
                                     }
                                     return signedIn;
                                 })
                                 .recover(err -> Future.failedFuture(err instanceof UserDisabledException
                                         ? new OidcCallbackException(OidcErrorCodes.USER_DISABLED)
                                         : err));
        } catch (OidcCallbackException refused) {
            ret = Future.failedFuture(refused);
        }
        return ret;
    }

    // the configuration the system console's login offers under the id, or null
    private Future<OidcConfiguration> offered(String configId) {
        return oidcConfigurationService.findOffered(configId, DomainUtil.SYSTEM_ID, null, null);
    }

    private String callbackUrl(String configId) {
        return OidcCallbacks.systemCallbackUrl(managementApiProperties.getManagementApi().getSystemApiBaseUrl(), configId);
    }
}
