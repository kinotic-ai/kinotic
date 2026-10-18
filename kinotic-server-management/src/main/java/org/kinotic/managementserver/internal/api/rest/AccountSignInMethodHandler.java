package org.kinotic.managementserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.model.security.OrganizationOidcConfiguration;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.CallbackResult;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.rest.support.OidcFlowPurpose;
import org.kinotic.domain.api.rest.support.PendingFlow;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.managementserver.api.config.ManagementServerProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Connects a provider identity to the signed-in account as a further sign-in method: one of the platform's
 * registrations, or an organization's own configuration where the account is a member. The person is already
 * signed in, which is what proves the account is theirs: the provider round-trip then asserts the identity,
 * and the configuration's callback at {@link OrganizationOidcCallbackHandler} hands the result back here to
 * bind. Nothing here signs anyone in; a sign-in through the new binding runs through
 * {@link OrganizationLoginHandler} like any other.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountSignInMethodHandler implements SuppliesGatewayRoutes, OidcFlowCompleter {

    private static final String PROFILE_PATH = "/account/profile";

    /** The identity is bound to another account already. */
    private static final String IDENTITY_IN_USE = "identity_in_use";

    private final AuthEndpointSupport authEndpointSupport;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final ParticipantIdentityService identityService;
    private final ManagementServerProperties properties;

    @Override
    public void mountRoutes(Router router) {
        router.post("/api/auth/account/methods/start/:configurationId").handler(this::handleStart);
    }

    /**
     * {@code POST /api/auth/account/methods/start/:configurationId} — begins the round-trip to a configuration
     * for the signed-in account, redirecting the browser to the provider: one of the platform's org sign-up
     * registrations, or an organization's own configuration when the account may sign in to that organization.
     * {@code 400} for any other configuration, {@code 403} for an organization's the account may not sign in
     * to, {@code 401} without a signed-in user.
     */
    private void handleStart(RoutingContext ctx) {
        Participant account = authEndpointSupport.requireSessionUser(ctx);
        String configurationId = ctx.pathParam("configurationId");
        oidcConfigurationService.findById(configurationId)
              .compose(config -> {
                  Future<String> ret;
                  if (config instanceof OrgSignupOidcConfiguration signup && signup.isEnabled()) {
                      ret = oidcFlowOrchestrator.startConnect(ctx, signup,
                                                              OrganizationOidcCallbacks.socialCallbackUrl(properties.getApiBaseUrl(), signup.getId()),
                                                              null, account.getId());
                  } else if (config instanceof OrganizationOidcConfiguration owned && owned.isEnabled()) {
                      // an organization's configuration binds only for an account that may sign in there
                      ret = identityService.resolveParticipant(account.getId(), owned.getOrganizationId(), null)
                                           .compose(member -> member == null
                                                   ? Future.failedFuture(new HttpException(403))
                                                   : oidcFlowOrchestrator.startConnect(ctx, owned,
                                                                                       OrganizationOidcCallbacks.ssoCallbackUrl(properties.getApiBaseUrl(), owned.getId()),
                                                                                       owned.getOrganizationId(), account.getId()));
                  } else {
                      ret = Future.failedFuture(new IllegalArgumentException("Unknown or disabled configuration: " + configurationId));
                  }
                  return ret;
              })
              .onSuccess(url -> ctx.response().setStatusCode(302).putHeader("Location", url).end())
              .onFailure(err -> {
                  if (err instanceof HttpException http && http.getStatusCode() == 403) {
                      authEndpointSupport.respondError(ctx, 403, "You are not a member of that organization");
                  } else if (err instanceof IllegalArgumentException) {
                      authEndpointSupport.respondError(ctx, 400, err.getMessage());
                  } else {
                      log.error("Connecting configuration {} failed to start", configurationId, err);
                      authEndpointSupport.respondError(ctx, 500, "Provider initialization failed");
                  }
              });
    }

    @Override
    public OidcFlowPurpose purpose() {
        return OidcFlowPurpose.CONNECT;
    }

    /**
     * Binds the asserted identity to the account that started the flow, then sends the browser back to the
     * profile page with {@code connected=<provider>}, or with {@code error=identity_in_use} when the identity
     * already signs in another account.
     */
    @Override
    public void complete(RoutingContext ctx, CallbackResult<OidcConfiguration> result) {
        String sub = authEndpointSupport.verifiedSubject(ctx, result);
        if (sub != null) {
            identityService.bindOidcIdentity(result.accountId(), result.config().getId(), sub)
                           .onSuccess(binding -> authEndpointSupport.redirectToUi(ctx, PROFILE_PATH + "?connected="
                                   + URLEncoder.encode(result.config().getName(), StandardCharsets.UTF_8)))
                           .onFailure(err -> {
                               String code = err instanceof IllegalArgumentException ? IDENTITY_IN_USE : "exchange_failed";
                               log.warn("Could not connect a sign-in method to account {}: {}", result.accountId(), err.getMessage());
                               authEndpointSupport.redirectToUi(ctx, PROFILE_PATH + "?error=" + code);
                           });
        }
    }

    @Override
    public void fail(RoutingContext ctx, PendingFlow flow, Throwable failure) {
        authEndpointSupport.redirectToUi(ctx, PROFILE_PATH + "?error=" + authEndpointSupport.callbackErrorCode(failure));
    }
}
