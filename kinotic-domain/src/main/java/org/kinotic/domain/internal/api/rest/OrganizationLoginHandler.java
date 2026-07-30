package org.kinotic.domain.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.internal.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.internal.api.rest.support.CallbackResult;
import org.kinotic.domain.internal.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.OidcConfiguration;
import org.kinotic.domain.api.model.iam.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.api.services.iam.LocalAuthenticationService;
import org.kinotic.domain.api.services.iam.OidcConfigurationService;
import org.kinotic.domain.api.services.iam.OrgSignupOidcConfigurationService;
import org.kinotic.domain.internal.api.repositories.OidcConfigurationRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Login routes for an organization — email/password, email-first SSO redirect, and social-button
 * (OIDC) login. On success each establishes the browser session; the STOMP WebSocket handshake then
 * authenticates from that session cookie, so the browser never handles a token. OIDC flows started
 * here return to this handler's own callbacks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationLoginHandler implements SuppliesGatewayRoutes {

    private final AuthEndpointSupport authEndpointSupport;
    private final IamUserService iamUserService;
    private final LocalAuthenticationService localAuthenticationService;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final OidcConfigurationRepository oidcConfigurationRepository;

    @Override
    public void mountRoutes(Router router) {
        router.get("/api/auth/org/login/providers").handler(this::handleProviders);
        router.post("/api/auth/org/login/lookup").handler(this::handleLookup);
        router.post("/api/auth/org/login").handler(this::handleLogin);
        router.post("/api/auth/org/login/social/start/:provider").handler(this::handleSocialStart);
        router.get("/api/auth/org/login/social/callback/:configId").handler(this::handleSocialCallback);
        router.get("/api/auth/org/login/sso/callback/:configId").handler(this::handleSsoCallback);
    }

    /**
     * {@code POST /api/auth/org/login/lookup {email}} — decides how the user authenticates: an SSO redirect
     * for an OIDC org member whose org has a live login config, otherwise "use password".
     */
    private void handleLookup(RoutingContext ctx) {
        JsonObject body = authEndpointSupport.readJsonBody(ctx);
        if (body == null) {
            return;
        }
        String email = body.getString("email");
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
     * {@code GET /api/auth/org/login/providers} — the unique provider keys (e.g. {@code "google"},
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
     * {@code GET /api/auth/org/login/social/callback/:configId} — the social IdP returns here; validates the
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
                sub -> iamUserService.findOrgUserByOidcIdentity(sub, result.config().getId()));
    }

    /**
     * {@code POST /api/auth/org/login/social/start/:provider} — begins social (OIDC) login by redirecting the
     * browser to the chosen Kinotic-curated provider.
     */
    private void handleSocialStart(RoutingContext ctx) {
        authEndpointSupport.handleSocialStart(ctx, this::socialCallbackUrl);
    }

    /**
     * {@code GET /api/auth/org/login/sso/callback/:configId} — the org's SSO IdP returns here; validates the
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
     * {@code POST /api/auth/org/login {email, password}} — verifies the password and establishes the browser
     * session. Generic {@code 401} on any failure.
     */
    private void handleLogin(RoutingContext ctx) {
        authEndpointSupport.handlePasswordLogin(ctx, localAuthenticationService::authenticateLocal);
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
        return authEndpointSupport.absoluteUrl("/api/auth/org/login/social/callback/" + configId);
    }

    private String ssoCallbackUrl(String configId) {
        return authEndpointSupport.absoluteUrl("/api/auth/org/login/sso/callback/" + configId);
    }
}
