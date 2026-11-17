package org.kinotic.systemserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.common.api.oidc.OAuth2Util;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.core.api.service.SuppliesGatewayRoutes;
import org.kinotic.domain.api.exceptions.ExistingUserException;
import org.kinotic.domain.api.model.security.SystemOidcConfiguration;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.CallbackResult;
import org.kinotic.common.api.oidc.OidcCallbackException;
import org.kinotic.domain.api.rest.support.OidcCallbacks;
import org.kinotic.common.api.oidc.OidcErrorCodes;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.rest.support.PendingFlow;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.systemserver.api.config.SystemServerProperties;
import org.springframework.stereotype.Component;

/**
 * Login routes for system users ({@code SystemParticipantIdentity}): with the system user's password, or through
 * one of the system's own registrations ({@link SystemOidcConfiguration}) with its operators' identity provider.
 * System users have no sign-up. On success the browser session is established; the STOMP WebSocket handshake then
 * authenticates from that session cookie.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemLoginHandler implements SuppliesGatewayRoutes {

    private final AuthEndpointSupport authEndpointSupport;
    private final LocalAuthenticationService localAuthenticationService;
    private final ParticipantIdentityService identityService;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final SystemServerProperties properties;

    @Override
    public void mountRoutes(Router router) {
        router.post("/api/auth/system/login").handler(this::handleLogin);
        router.get("/api/auth/system/login/providers").handler(this::handleProviders);
        router.post("/api/auth/system/login/start/:configId").handler(this::handleStart);
        router.get(OidcCallbacks.SYSTEM_CALLBACK_PATH + ":configId").handler(this::handleCallback);
    }

    /**
     * {@code POST /api/auth/system/login {email, password}} — the system user's password, so an organization's or an
     * application's user with the same email cannot authenticate here. Generic {@code 401} on any failure.
     */
    private void handleLogin(RoutingContext ctx) {
        authEndpointSupport.handlePasswordLogin(ctx,
                (email, password) -> localAuthenticationService.authenticateSystemUser(email, password)
                                                               .compose(user -> user == null
                                                                       ? Future.succeededFuture()
                                                                       : identityService.resolveParticipant(user, null, null)));
    }

    /** {@code GET /api/auth/system/login/providers} — the registrations the login page offers, as {@code [{id, type, name, provider}]}. */
    private void handleProviders(RoutingContext ctx) {
        oidcConfigurationService.findEnabledSystemProviders()
                                .onSuccess(configs -> ctx.json(authEndpointSupport.providersJson(configs)))
                                .onFailure(ctx::fail);
    }

    /**
     * {@code POST /api/auth/system/login/start/:configId} — sends the browser to the provider of the registration
     * {@code configId}. A {@code referer} query parameter naming a path within the console is where the browser
     * lands once signed in.
     */
    private void handleStart(RoutingContext ctx) {
        String configId = ctx.pathParam("configId");
        oidcConfigurationService.findSystemById(configId)
                                .onSuccess(config -> {
                                    if (config == null || !config.isEnabled()) {
                                        authEndpointSupport.redirectError(ctx, SessionBinding.origin(ctx), OidcErrorCodes.CONFIG_NOT_FOUND);
                                    } else {
                                        authEndpointSupport.redirectToIdp(ctx, config, callbackUrl(configId), null, SessionBinding.origin(ctx));
                                    }
                                })
                                .onFailure(ctx::fail);
    }

    /**
     * The provider's callback for the registration {@code :configId}: signs in the account the provider verified
     * when its email is on the registration's domain, as the system user of that email, which it creates on the
     * account's first sign-in.
     */
    private void handleCallback(RoutingContext ctx) {
        String configId = ctx.pathParam("configId");
        // read before the callback is handled, which consumes the flow: a refused callback returns to the page that started it
        PendingFlow pending = oidcFlowOrchestrator.pendingFlow(ctx);
        oidcFlowOrchestrator.handleCallback(ctx, configId, callbackUrl(configId),
                                            (orgId, applicationId, tenantId) -> oidcConfigurationService.findSystemById(configId))
                            .onSuccess(result -> authEndpointSupport.completeLogin(ctx, result.origin(), result.config().getProvider(),
                                                                                    signIn(result)))
                            .onFailure(err -> authEndpointSupport.redirectCallbackFailure(ctx, pending != null ? pending.origin() : null, err));
    }

    private Future<Participant> signIn(CallbackResult<SystemOidcConfiguration> result) {
        SystemOidcConfiguration config = result.config();
        String email = OAuth2Util.stringClaim(result.claims(), "email");
        Future<Participant> ret;
        if (email == null) {
            // Entra states an address only for an account that has one
            log.warn("A sign-in through {} was refused: the account states no email", config.getId());
            ret = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.ACCOUNT_NOT_ALLOWED, config.getProvider()));
        } else if (!OAuth2Util.isEmailVerified(result.claims(), config.getProvider())) {
            ret = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.EMAIL_NOT_VERIFIED, config.getProvider()));
        } else if (!email.substring(email.lastIndexOf('@') + 1).equalsIgnoreCase(config.getAllowedEmailDomain())) {
            log.warn("A sign-in through {} was refused: the account is outside {}", config.getId(), config.getAllowedEmailDomain());
            ret = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.ACCOUNT_NOT_ALLOWED, config.getProvider()));
        } else {
            ret = identityService.findSystemUserByEmail(email)
                                 .compose(user -> user != null ? Future.succeededFuture(user) : createSystemUser(email, result))
                                 .compose(user -> identityService.resolveParticipant(user, null, null));
        }
        return ret;
    }

    private Future<SystemParticipantIdentity> createSystemUser(String email, CallbackResult<SystemOidcConfiguration> result) {
        SystemParticipantIdentity user = new SystemParticipantIdentity();
        user.setEmail(email);
        user.setDisplayName(OAuth2Util.stringClaim(result.claims(), "name"));
        log.info("Creating the system user {} on its first sign-in through {}", email, result.config().getId());
        return identityService.createUser(user, null)
                              // another sign-in of the account created it meanwhile
                              .recover(err -> err instanceof ExistingUserException
                                      ? identityService.findSystemUserByEmail(email)
                                      : Future.failedFuture(err));
    }

    private String callbackUrl(String configId) {
        return OidcCallbacks.systemCallbackUrl(properties.getApiBaseUrl(), configId);
    }
}
