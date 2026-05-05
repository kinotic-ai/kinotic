package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.gateway.internal.auth.LoginResponses;
import org.kinotic.gateway.internal.auth.OidcFlowOrchestrator;
import org.kinotic.gateway.internal.auth.SessionKeys;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.model.iam.OidcProviderKind;
import org.kinotic.os.api.model.iam.OrgSignupOidcConfiguration;
import org.kinotic.os.api.services.iam.IamUserService;
import org.kinotic.os.api.services.iam.LocalAuthenticationService;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.kinotic.os.api.services.iam.OrgSignupOidcConfigurationService;
import org.kinotic.os.api.services.iam.PendingRegistrationService;
import org.kinotic.os.internal.api.model.iam.IamCredential;
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
 *   <li><b>Email + password → token</b>: {@code POST /api/login/token {email, password}} —
 *       verifies bcrypt against {@link IamCredential}, mints the same Kinotic JWT the
 *       OIDC paths produce, and returns {@code {token}}. Generic {@code 401} for any
 *       failure.</li>
 * </ul>
 *
 * <p>The OIDC dance itself (state/PKCE generation, callback validation, code exchange,
 * claim flattening, issuer validation) lives in {@link OidcFlowOrchestrator}; response
 * shaping (JWT minting, error/success redirects, JSON bodies) lives in
 * {@link LoginResponses}. This handler only wires routes, decides what config to start
 * with, and decides what to do with the resulting claims.
 *
 * <p>Callback paths are split per config type ({@code /sso/:id} vs {@code /social/:id})
 * so the resolver looks up an unambiguous service per route.
 *
 * <p>{@code GET /api/login/providers} returns the unique provider keys from the
 * Kinotic-curated social configs for rendering the social buttons.
 * {@code POST /api/register/complete} consumes a {@link org.kinotic.os.api.model.iam.PendingRegistration}
 * from the {@link org.kinotic.os.api.model.iam.UserProvisioningMode#REGISTRATION_REQUIRED}
 * signup path.
 *
 * <p>Direct STOMP CONNECT with {@code login}/{@code passcode}/{@code authScopeType}/{@code
 * authScopeId} headers stays available for non-UI clients (CLI, automation) that already
 * know the target scope. The browser SPA does not use that path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationLoginHandler {

    /** Stashed on the SSO path so the callback can narrow the IamUser lookup. */
    private static final String S_ORG_ID = "oidc.orgId";

    private static final SessionKeys SSO_SESSION_KEYS = SessionKeys.ofPrefix("oidc", S_ORG_ID);
    private static final SessionKeys SOCIAL_SESSION_KEYS = SessionKeys.ofPrefix("oidc-social");

    private final Vertx vertx;
    private final IamUserService iamUserService;
    private final OidcConfigurationService oidcConfigurationService;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final LocalAuthenticationService localAuthenticationService;
    private final PendingRegistrationService pendingRegistrationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final SecurityContext securityContext;
    private final LoginResponses loginResponses;

    public void mountRoutes(Router router) {
        // BodyHandler is already installed at /api/* by SignUpHandler; not duplicated here.
        router.route("/api/login/*").handler(RedirectFlowSessionSupport.newSessionHandler(vertx));

        router.get("/api/login/providers").handler(this::handleProviders);
        router.post("/api/login/lookup").handler(this::handleLookup);
        router.post("/api/login/token").handler(this::handleToken);
        router.post("/api/login/start/:provider").handler(this::handleSocialStart);
        router.get("/api/login/callback/social/:configId").handler(this::handleSocialCallback);
        router.get("/api/login/callback/sso/:configId").handler(this::handleSsoCallback);
        router.post("/api/register/complete").handler(this::handleRegisterComplete);
    }

    private void handleToken(RoutingContext ctx) {
        loginResponses.handlePasswordToken(ctx, localAuthenticationService::authenticateLocal);
    }

    /**
     * Unique provider keys (e.g. {@code "google"}, {@code "microsoft-live"}) for rendering
     * the social-button row. Distinct from {@link LoginResponses#respondProvidersList}
     * which returns full config metadata — here multiple configs may share a provider
     * kind and the frontend just needs one button per kind.
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
                  loginResponses.respondError(ctx, 500, "Failed to list providers");
              });
    }

    private void handleLookup(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String email = body == null ? null : body.getString("email");
        if (email == null || email.isBlank()) {
            loginResponses.respondError(ctx, 400, "email is required");
            return;
        }

        Future.fromCompletionStage(iamUserService.findByEmail(email))
              .compose(user -> resolveSsoOrPassword(ctx, user))
              .onFailure(err -> {
                  log.warn("Login lookup failed for {}: {}", email, err.getMessage());
                  loginResponses.respondError(ctx, 500, "Lookup failed");
              });
    }

    /**
     * Returns the JSON body for the lookup. If the user is missing, local, or has a dead
     * OIDC config → "password" (frontend reveals password field). If OIDC and the org
     * config is live → "sso" with a pre-built redirect URL; the session cookie is set so
     * the callback can validate state.
     */
    private Future<Void> resolveSsoOrPassword(RoutingContext ctx, IamUser user) {
        if (user == null || user.getAuthType() == null
                || !"OIDC".equals(user.getAuthType().name())
                || !"ORGANIZATION".equals(user.getAuthScopeType())) {
            return loginResponses.respondPasswordPath(ctx);
        }

        String orgId = user.getAuthScopeId();
        return Future.fromCompletionStage(oidcConfigurationService.findOrgLoginConfig(orgId))
                     .compose(match -> {
                         if (match == null) {
                             // Org has no live ORG_LOGIN config — fall back to password (which will
                             // fail with invalid creds since OIDC users have no password).
                             // Deliberately generic so we don't leak which orgs use SSO.
                             return loginResponses.respondPasswordPath(ctx);
                         }
                         return oidcFlowOrchestrator.startFlow(ctx, match, SSO_SESSION_KEYS,
                                                               ssoCallbackUrl(match.getId()),
                                                               Map.of(S_ORG_ID, orgId))
                                 .compose(url -> loginResponses.respondSsoRedirect(ctx, url));
                     });
    }

    private void handleSocialStart(RoutingContext ctx) {
        String provider = ctx.pathParam("provider");
        OidcProviderKind providerKind;
        try {
            providerKind = OidcProviderKind.fromKey(provider);
        } catch (IllegalArgumentException ex) {
            loginResponses.respondError(ctx, 400, "Unknown platform provider: " + provider);
            return;
        }

        Future.fromCompletionStage(orgSignupOidcConfigurationService.findEnabledByProvider(providerKind))
              .compose(config -> {
                  if (config == null) {
                      loginResponses.respondError(ctx, 400, "Unknown or disabled platform provider: " + provider);
                      return Future.<String>succeededFuture();
                  }
                  return oidcFlowOrchestrator.startFlow(ctx, config, SOCIAL_SESSION_KEYS,
                                                       socialCallbackUrl(config.getId()), null);
              })
              .onSuccess(url -> {
                  if (url != null) {
                      ctx.response().setStatusCode(302).putHeader("Location", url).end();
                  }
              })
              .onFailure(ex -> {
                  log.error("Social login start failed for {}", provider, ex);
                  loginResponses.respondError(ctx, 500, "Provider initialization failed");
              });
    }

    private void handleSocialCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.<OrgSignupOidcConfiguration>handleCallback(
                ctx, pathConfigId, SOCIAL_SESSION_KEYS, socialCallbackUrl(pathConfigId),
                orgSignupOidcConfigurationService::findById)
                .onSuccess(result -> loginResponses.completeOidcLogin(ctx, result.config(), result.claims(),
                        // Social login: identity might exist in any org; pick the first match.
                        sub -> iamUserService.findByOidcIdentity(sub, result.config().getId())
                                             .thenApply(this::pickFirst)))
                .onFailure(ex -> loginResponses.redirectCallbackFailure(ctx, ex));
    }

    private void handleSsoCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        // OidcConfiguration is OrganizationScoped; the pre-auth callback has no
        // participant bound, so the lookup runs with elevated access. The configId is
        // trusted — it came from the IdP redirect we issued ourselves under the same id.
        oidcFlowOrchestrator.<OidcConfiguration>handleCallback(
                ctx, pathConfigId, SSO_SESSION_KEYS, ssoCallbackUrl(pathConfigId),
                id -> securityContext.withElevatedAccess(() -> oidcConfigurationService.findById(id)))
                .onSuccess(result -> {
                    String orgId = result.extras().get(S_ORG_ID);
                    loginResponses.completeOidcLogin(ctx, result.config(), result.claims(),
                            sub -> iamUserService.findByOidcIdentityAndScope(
                                    sub, result.config().getId(), "ORGANIZATION", orgId));
                })
                .onFailure(ex -> loginResponses.redirectCallbackFailure(ctx, ex));
    }

    private IamUser pickFirst(List<IamUser> candidates) {
        return (candidates == null || candidates.isEmpty()) ? null : candidates.getFirst();
    }

    private void handleRegisterComplete(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String token = body == null ? null : body.getString("token");
        if (token == null || token.isBlank()) {
            loginResponses.respondError(ctx, 400, "token is required");
            return;
        }
        String displayNameOverride = body.getString("displayName");

        Future.fromCompletionStage(pendingRegistrationService.complete(token, user -> {
            if (displayNameOverride != null && !displayNameOverride.isBlank()) {
                user.setDisplayName(displayNameOverride);
            }
        })).onSuccess(user -> loginResponses.respondJwt(ctx, user))
          .onFailure(ex -> {
              Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
              loginResponses.respondError(ctx, 400, cause.getMessage());
          });
    }

    private String socialCallbackUrl(String configId) {
        return loginResponses.apiBase() + "/api/login/callback/social/" + configId;
    }

    private String ssoCallbackUrl(String configId) {
        return loginResponses.apiBase() + "/api/login/callback/sso/" + configId;
    }
}
