package org.kinotic.managementserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.Participant;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.model.security.OrganizationOidcConfiguration;
import org.kinotic.core.api.service.SuppliesGatewayRoutes;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.CallbackResult;
import org.kinotic.domain.api.rest.support.OidcCallbackException;
import org.kinotic.domain.api.rest.support.OidcCallbacks;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.rest.support.OidcFlowPurpose;
import org.kinotic.domain.api.rest.support.PendingFlow;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Connects a provider identity to the signed-in organization user as a further sign-in method: one of the
 * system's registrations, or an organization's own configuration where the user is a member. The person is
 * already signed in, which is what proves the organization user is theirs: the provider round-trip then asserts the identity,
 * and the configuration's callback at {@link OrganizationOidcCallbackHandler} hands the result back here to
 * bind. Nothing here signs anyone in; a sign-in through the new binding runs through
 * {@link OrganizationLoginHandler} like any other.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationUserSignInMethodHandler implements SuppliesGatewayRoutes, OidcFlowCompleter {

    private static final String PROFILE_PATH = "/account/profile";

    /** The identity is bound to another organization user already. */
    private static final String IDENTITY_IN_USE = "identity_in_use";

    private final AuthEndpointSupport authEndpointSupport;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final ParticipantIdentityService identityService;
    private final KinoticManagementApiProperties managementApiProperties;

    @Override
    public void mountRoutes(Router router) {
        router.post("/api/auth/org/methods/start/:configurationId").handler(this::handleStart);
    }

    /**
     * {@code POST /api/auth/org/methods/start/:configurationId} — begins the round-trip to a configuration
     * for the signed-in organization user, redirecting the browser to the provider: one of the system's org sign-up
     * registrations, or an organization's own configuration when the user may sign in to that organization.
     * {@code 400} for any other configuration, {@code 403} for an organization's the user may not sign in
     * to, {@code 401} without a signed-in user.
     */
    private void handleStart(RoutingContext ctx) {
        Participant user = authEndpointSupport.requireSessionUser(ctx);
        String configurationId = ctx.pathParam("configurationId");
        oidcConfigurationService.findById(configurationId)
              .compose(config -> {
                  Future<String> ret;
                  if (config instanceof OrgSignupOidcConfiguration signup && signup.isEnabled()) {
                      ret = oidcFlowOrchestrator.startConnect(ctx, signup,
                                                              OidcCallbacks.socialCallbackUrl(managementApiProperties.getManagementApi().getApiBaseUrl(), signup.getId()),
                                                              null, user.getId());
                  } else if (config instanceof OrganizationOidcConfiguration owned && owned.isEnabled()) {
                      // an organization's configuration binds only for a user that may sign in there: the organization
                      // admitted the user, so its directory may vouch for it; a link can start this for no other user
                      ret = identityService.resolveParticipant(user.getId(), owned.getOrganizationId(), null)
                                           .compose(member -> member == null
                                                   ? Future.failedFuture(new HttpException(403))
                                                   : oidcFlowOrchestrator.startConnect(ctx, owned,
                                                                                       OidcCallbacks.ssoCallbackUrl(managementApiProperties.getManagementApi().getApiBaseUrl(), owned.getId()),
                                                                                       owned.getOrganizationId(), user.getId()));
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
     * Binds the asserted identity to the organization user that started the flow, then sends the browser back to the
     * profile page with {@code connected=<provider>}, or with {@code error=identity_in_use} when the identity
     * already signs in another organization user, and {@code error=email_not_verified&provider=<key>} when the
     * provider has not verified the identity's email.
     */
    @Override
    public void complete(RoutingContext ctx, CallbackResult<OidcConfiguration> result) {
        Future.succeededFuture(result)
              .map(authEndpointSupport::requireVerifiedSubject)
              .compose(sub -> identityService.bindOidcIdentity(result.organizationUserId(), result.config().getId(), sub))
              .onSuccess(binding -> authEndpointSupport.redirectToUi(ctx, PROFILE_PATH + "?connected="
                      + URLEncoder.encode(result.config().getName(), StandardCharsets.UTF_8)))
              .onFailure(err -> {
                  String code = err instanceof IllegalArgumentException ? IDENTITY_IN_USE : authEndpointSupport.callbackErrorCode(err);
                  log.warn("Could not connect a sign-in method to organization user {}: {}", result.organizationUserId(), err.getMessage());
                  String provider = err instanceof OidcCallbackException oce ? AuthEndpointSupport.providerParameter(oce.getProvider()) : "";
                  authEndpointSupport.redirectToUi(ctx, PROFILE_PATH + "?error=" + code + provider);
              });
    }

    @Override
    public void fail(RoutingContext ctx, PendingFlow flow, Throwable failure) {
        authEndpointSupport.redirectToUi(ctx, PROFILE_PATH + "?error=" + authEndpointSupport.callbackErrorCode(failure));
    }
}
