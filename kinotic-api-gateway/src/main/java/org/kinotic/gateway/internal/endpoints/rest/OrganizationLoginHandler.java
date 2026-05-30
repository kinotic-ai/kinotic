package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.gateway.internal.endpoints.rest.support.AuthEndpointSupport;
import org.kinotic.gateway.internal.endpoints.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.OidcConfiguration;
import org.kinotic.domain.api.model.iam.OidcProviderKind;
import org.kinotic.domain.api.model.iam.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.api.services.iam.LocalAuthenticationService;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.kinotic.domain.api.services.iam.OrgSignupOidcConfigurationService;
import org.kinotic.domain.api.services.iam.PendingRegistrationService;
import org.kinotic.domain.internal.api.model.IamCredential;
import org.kinotic.domain.internal.api.repositories.OidcConfigurationRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Login routes for the kinotic-server. Every browser path converges on a short-TTL,
 * Kinotic-signed JWT that the frontend uses as the {@code Bearer} on STOMP CONNECT —
 * the SPA never sends raw credentials over the WebSocket.
 *
 * <h3>Three entry points</h3>
 * <ul>
 *   <li><b>Social button</b>: {@code POST /api/login/start/:provider} — picks the
 *       Kinotic-curated {@link OrgSignupOidcConfiguration} for that provider key and
 *       redirects the browser to the IdP. Callback lands at
 *       {@code /api/login/callback/social/:configId}.</li>
 *   <li><b>Email lookup → SSO redirect</b>: {@code POST /api/login/lookup {email}} — when
 *       the user's org membership is OIDC and the org's {@code ORG_LOGIN}-scoped
 *       {@link OidcConfiguration} is live, returns {@code {type: "sso", redirect: "..."}}.
 *       Otherwise returns {@code {type: "password"}}. Deliberately ambiguous on unknown
 *       email / local user / dead SSO config to avoid leaking which orgs use SSO.
 *       Callback lands at {@code /api/login/callback/sso/:configId}.</li>
 *   <li><b>Email + password → session</b>: {@code POST /api/login {email, password}} —
 *       verifies bcrypt against {@link IamCredential} and establishes the browser session.
 *       Generic {@code 401} for any failure.</li>
 * </ul>
 *
 * <p>The OIDC dance itself (state/PKCE generation, callback validation, code exchange,
 * claim flattening, issuer validation) lives in {@link OidcFlowOrchestrator}; response
 * shaping (JWT minting, error/success redirects, JSON bodies) lives in
 * {@link AuthEndpointSupport}. This handler only wires routes, decides what config to start
 * with, and decides what to do with the resulting claims.
 *
 * <p>Callback paths are split per config type ({@code /sso/:id} vs {@code /social/:id})
 * so the resolver looks up an unambiguous service per route.
 *
 * <p>{@code GET /api/login/providers} returns the unique provider keys from the
 * Kinotic-curated social configs for rendering the social buttons.
 * {@code POST /api/register/complete} consumes a {@link org.kinotic.domain.api.model.iam.PendingRegistration}
 * from the {@link org.kinotic.domain.api.model.iam.UserProvisioningMode#REGISTRATION_REQUIRED}
 * signup path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationLoginHandler {

    /** Stashed on the SSO path so the callback can narrow the IamUser lookup. */
    private static final String ORG_ID_EXTRA = "orgId";
    private final AuthEndpointSupport authEndpointSupport;
    private final IamUserService iamUserService;
    private final LocalAuthenticationService localAuthenticationService;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final PendingRegistrationService pendingRegistrationService;
    private final OidcConfigurationRepository oidcConfigurationRepository;

    public void mountRoutes(Router router) {
        router.get(OidcConstants.ORG_LOGIN_BASE + "/providers").handler(this::handleProviders);
        router.post(OidcConstants.ORG_LOGIN_BASE + "/lookup").handler(this::handleLookup);
        router.post(OidcConstants.ORG_LOGIN_BASE).handler(this::handleLogin);
        router.post(OidcConstants.ORG_LOGIN_BASE + "/start/:provider").handler(this::handleSocialStart);
        router.get(OidcConstants.ORG_LOGIN_BASE + "/callback/social/:configId").handler(this::handleSocialCallback);
        router.get(OidcConstants.ORG_LOGIN_BASE + "/callback/sso/:configId").handler(this::handleSsoCallback);
        router.post(OidcConstants.ORG_REGISTER_COMPLETE).handler(this::handleRegisterComplete);
    }

    private void handleLookup(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String email = body == null ? null : body.getString("email");
        if (email == null || email.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "email is required");
            return;
        }

        Future.fromCompletionStage(iamUserService.findByEmail(email))
              .compose(user -> resolveSsoOrPassword(ctx, user))
              .onFailure(err -> {
                  log.warn("Login lookup failed for {}: {}", email, err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Lookup failed");
              });
    }

    /**
     * Unique provider keys (e.g. {@code "google"}, {@code "microsoft-live"}) for rendering
     * the social-button row. Distinct from {@link AuthEndpointSupport#respondProvidersList}
     * which returns full config metadata — here multiple configs may share a provider
     * kind, and the frontend just needs one button per kind.
     */
    private void handleProviders(RoutingContext ctx) {
        Future.fromCompletionStage(orgSignupOidcConfigurationService.findAllEnabled())
              .onSuccess(configs -> {
                  JsonArray providers = new JsonArray();
                  Set<String> seen = new LinkedHashSet<>();
                  for (OrgSignupOidcConfiguration c : configs) {
                      if (c.getProvider() == null) continue;
                      String key = c.getProvider().key();
                      if (seen.add(key)) providers.add(key);
                  }
                  ctx.response().putHeader("Content-Type", "application/json").end(providers.encode());
              })
              .onFailure(err -> {
                  log.warn("Failed to list platform providers: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Failed to list providers");
              });
    }

    private void handleRegisterComplete(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String token = body == null ? null : body.getString("token");
        if (token == null || token.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "token is required");
            return;
        }
        @SuppressWarnings("null")
        String displayNameOverride = body.getString("displayName");

        Future.fromCompletionStage(pendingRegistrationService.complete(token, user -> {
                  if (displayNameOverride != null && !displayNameOverride.isBlank()) {
                      user.setDisplayName(displayNameOverride);
                  }
              })).onSuccess(user -> authEndpointSupport.respondSuccess(ctx, user))
              .onFailure(ex -> {
                  Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                  authEndpointSupport.respondError(ctx, 400, cause.getMessage());
              });
    }

    private void handleSocialCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.handleCallback(ctx,
                                            pathConfigId,
                                            socialCallbackUrl(pathConfigId),
                                            (id, extras) -> orgSignupOidcConfigurationService.findById(id))
                            .onSuccess(result -> authEndpointSupport.completeOidcLogin(ctx,
                                                                                       result.config(),
                                                                                       result.claims(),
                                    // Social login: identity might exist in any org; pick the first match.
                                                                                       sub -> iamUserService.findAllByOidcIdentity(sub,
                                                                                                                                   result.config().getId())
                                                                                                            .thenApply(this::pickFirst)))
                            .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private void handleSocialStart(RoutingContext ctx) {
        String provider = ctx.pathParam("provider");
        OidcProviderKind providerKind;
        try {
            providerKind = OidcProviderKind.fromKey(provider);
        } catch (IllegalArgumentException ex) {
            authEndpointSupport.respondError(ctx, 400, "Unknown platform provider: " + provider);
            return;
        }

        Future.fromCompletionStage(orgSignupOidcConfigurationService.findEnabledByProvider(providerKind))
              .compose(config -> {
                  if (config == null) {
                      authEndpointSupport.respondError(ctx, 400, "Unknown or disabled platform provider: " + provider);
                      return Future.succeededFuture();
                  }
                  return oidcFlowOrchestrator.startFlow(ctx, config, socialCallbackUrl(config.getId()), null);
              })
              .onSuccess(url -> {
                  if (url != null) {
                      ctx.response().setStatusCode(302).putHeader("Location", url).end();
                  }
              })
              .onFailure(ex -> {
                  log.error("Social login start failed for {}", provider, ex);
                  authEndpointSupport.respondError(ctx, 500, "Provider initialization failed");
              });
    }

    private void handleSsoCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        // OidcConfiguration is OrganizationScoped; the pre-auth callback has no participant
        // bound, so the lookup is scoped by the orgId stashed on the flow session at startFlow.
        // The configId is trusted — it came from the IdP redirect we issued ourselves.
        oidcFlowOrchestrator.handleCallback(ctx,
                                            pathConfigId,
                                            ssoCallbackUrl(pathConfigId),
                                            (id, extras) -> oidcConfigurationRepository.findById(id, extras.get(ORG_ID_EXTRA)))
                            .onSuccess(result -> {
                                String orgId = result.extras().get(ORG_ID_EXTRA);
                                authEndpointSupport.completeOidcLogin(ctx,
                                                                      result.config(),
                                                                      result.claims(),
                                                                      sub -> iamUserService.findByOidcIdentity(sub,
                                                                                                                result.config().getId(),
                                                                                                                orgId,
                                                                                                                null));
                            })
                            .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private void handleLogin(RoutingContext ctx) {
        authEndpointSupport.handlePasswordLogin(ctx, localAuthenticationService::authenticateLocal);
    }

    private IamUser pickFirst(List<IamUser> candidates) {
        return (candidates == null || candidates.isEmpty()) ? null : candidates.getFirst();
    }

    /**
     * Returns the JSON body for the lookup. If the user is missing, local, or has a dead
     * OIDC config → "password" (frontend reveals password field). If OIDC and the org
     * config is live → "sso" with a pre-built redirect URL; the session cookie is set so
     * the callback can validate state.
     */
    private Future<Void> resolveSsoOrPassword(RoutingContext ctx, IamUser user) {
        if (user == null
                || user.getAuthType() != AuthType.OIDC
                || user.getOrganizationId() == null
                || user.getApplicationId() != null) {
            return authEndpointSupport.respondPasswordPath(ctx);
        }

        String orgId = user.getOrganizationId();
        return Future.fromCompletionStage(oidcConfigurationService.findOrgLoginConfig(orgId))
                     .compose(match -> {
                         if (match == null) {
                             // Org has no live ORG_LOGIN config — fall back to password (which will
                             // fail with invalid creds since OIDC users have no password).
                             // Deliberately generic so we don't leak which orgs use SSO.
                             return authEndpointSupport.respondPasswordPath(ctx);
                         }
                         return oidcFlowOrchestrator.startFlow(ctx,
                                                               match,
                                                               ssoCallbackUrl(match.getId()),
                                                               Map.of(ORG_ID_EXTRA, orgId))
                                                    .compose(url -> authEndpointSupport.respondSsoRedirect(ctx, url));
                     });
    }

    private String socialCallbackUrl(String configId) {
        return authEndpointSupport.absoluteUrl(OidcConstants.ORG_LOGIN_BASE + "/callback/social/" + configId);
    }

    private String ssoCallbackUrl(String configId) {
        return authEndpointSupport.absoluteUrl(OidcConstants.ORG_LOGIN_BASE + "/callback/sso/" + configId);
    }
}
