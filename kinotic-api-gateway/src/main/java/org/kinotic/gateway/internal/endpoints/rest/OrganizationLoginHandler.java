package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.gateway.internal.endpoints.rest.support.AuthEndpointSupport;
import org.kinotic.gateway.internal.endpoints.rest.support.CallbackResult;
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
import org.kinotic.domain.internal.api.repositories.OidcConfigurationRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Login routes for an organization — email/password, email-first SSO redirect, and social-button
 * (OIDC) login. On success each establishes the browser session; the STOMP WebSocket handshake then
 * authenticates from that session cookie, so the browser never handles a token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationLoginHandler {

    private final AuthEndpointSupport authEndpointSupport;
    private final IamUserService iamUserService;
    private final LocalAuthenticationService localAuthenticationService;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final OidcConfigurationRepository oidcConfigurationRepository;

    public void mountRoutes(Router router) {
        router.get(OidcConstants.ORG_LOGIN_BASE + "/providers").handler(this::handleProviders);
        router.post(OidcConstants.ORG_LOGIN_BASE + "/lookup").handler(this::handleLookup);
        router.post(OidcConstants.ORG_LOGIN_BASE).handler(this::handleLogin);
        router.post(OidcConstants.ORG_LOGIN_BASE + "/start/:provider").handler(this::handleSocialStart);
        router.get(OidcConstants.ORG_LOGIN_BASE + "/callback/social/:configId").handler(this::handleSocialCallback);
        router.get(OidcConstants.ORG_LOGIN_BASE + "/callback/sso/:configId").handler(this::handleSsoCallback);
    }

    /**
     * {@code POST /api/login/lookup {email}} — decides how the user authenticates: an SSO redirect
     * for an OIDC org member whose org has a live login config, otherwise "use password".
     */
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
     * {@code GET /api/login/providers} — the unique provider keys (e.g. {@code "google"},
     * {@code "microsoft-live"}) for rendering the social-button row: one key per provider kind,
     * even when several configs share a kind.
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

    /**
     * {@code GET /api/login/callback/social/:configId} — the social IdP returns here; validates the
     * callback and logs the user in. The identity may belong to any organization.
     */
    private void handleSocialCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.handleCallback(ctx,
                                            pathConfigId,
                                            socialCallbackUrl(pathConfigId),
                                            _ -> orgSignupOidcConfigurationService.findById(pathConfigId))
                            .onSuccess(result -> completeSocialLogin(ctx, result))
                            .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private void completeSocialLogin(RoutingContext ctx, CallbackResult<OrgSignupOidcConfiguration> result) {
        authEndpointSupport.completeOidcLogin(ctx, result.config(), result.claims(),
                // Social login: identity might exist in any org; pick the first match.
                sub -> iamUserService.findAllByOidcIdentity(sub, result.config().getId())
                                     .thenApply(this::pickFirst));
    }

    /**
     * {@code POST /api/login/start/:provider} — begins social (OIDC) login by redirecting the
     * browser to the chosen Kinotic-curated provider.
     */
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

    /**
     * {@code GET /api/login/callback/sso/:configId} — the org's SSO IdP returns here; validates the
     * callback and logs the user into that organization.
     */
    private void handleSsoCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        // OidcConfiguration is OrganizationScoped; the pre-auth callback has no participant
        // bound, so the lookup is scoped by the orgId stashed on the flow session at startFlow.
        // The configId is trusted — it came from the IdP redirect we issued ourselves.
        oidcFlowOrchestrator.handleCallback(ctx,
                                            pathConfigId,
                                            ssoCallbackUrl(pathConfigId),
                                            orgId -> oidcConfigurationRepository.findById(pathConfigId, orgId))
                            .onSuccess(result -> completeSsoLogin(ctx, result))
                            .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private void completeSsoLogin(RoutingContext ctx, CallbackResult<OidcConfiguration> result) {
        authEndpointSupport.completeOidcLogin(ctx, result.config(), result.claims(),
                sub -> iamUserService.findByOidcIdentity(sub, result.config().getId(), result.orgId(), null));
    }

    /**
     * {@code POST /api/login {email, password}} — verifies the password and establishes the browser
     * session. Generic {@code 401} on any failure.
     */
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
                                                               orgId)
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
