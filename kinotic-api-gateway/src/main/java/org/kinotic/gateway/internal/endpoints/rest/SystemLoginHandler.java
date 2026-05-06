package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.AuthScopeType;
import org.kinotic.gateway.internal.auth.AuthEndpointSupport;
import org.kinotic.gateway.internal.auth.OidcFlowOrchestrator;
import org.kinotic.gateway.internal.auth.SessionKeys;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.model.iam.SystemOidcConfiguration;
import org.kinotic.os.api.services.iam.IamUserService;
import org.kinotic.os.api.services.iam.SystemOidcConfigurationService;
import org.springframework.stereotype.Component;

/**
 * OIDC login routes for Kinotic platform administrators. Backed by
 * {@link SystemOidcConfiguration} (a separate Microsoft Entra app from the social-signup
 * configs) — admins are fully managed in Entra, no client secret stored, public-client
 * PKCE flow.
 *
 * <ul>
 *   <li>{@code GET /api/system/login/providers} — list available admin IdP configs for
 *       the login UI.</li>
 *   <li>{@code POST /api/system/login/start/:configId} — kick off the OIDC dance.</li>
 *   <li>{@code GET /api/system/login/callback/:configId} — IdP returns here.</li>
 * </ul>
 *
 * <p>System admins are pre-provisioned ({@link IamUser} with
 * {@code authScopeType=SYSTEM}, {@code authScopeId="kinotic"}); login is OIDC-only —
 * never auto-creates rows. The dev-only password fallback for {@code admin@kinotic.local}
 * lives on the org-login {@code /api/login/token} endpoint and is removed for production
 * deployments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemLoginHandler {

    private static final SessionKeys SESSION_KEYS = SessionKeys.ofPrefix("system-login");

    private final IamUserService iamUserService;
    private final SystemOidcConfigurationService systemOidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final AuthEndpointSupport authEndpointSupport;

    public void mountRoutes(Router router) {
        authEndpointSupport.installSessionHandler(router, OidcConstants.SYSTEM_LOGIN_BASE);

        router.get(OidcConstants.SYSTEM_LOGIN_BASE + "/providers").handler(this::handleProviders);
        router.post(OidcConstants.SYSTEM_LOGIN_BASE + "/start/:configId").handler(this::handleStart);
        router.get(OidcConstants.SYSTEM_LOGIN_BASE + "/callback/:configId").handler(this::handleCallback);
    }

    private void handleProviders(RoutingContext ctx) {
        Future.fromCompletionStage(systemOidcConfigurationService.findAllEnabled())
              .onSuccess(configs -> authEndpointSupport.respondProvidersList(ctx, configs))
              .onFailure(err -> {
                  log.warn("Failed to list system OIDC providers: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Failed to list providers");
              });
    }

    private void handleStart(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        Future.fromCompletionStage(systemOidcConfigurationService.findById(pathConfigId))
              .compose(config -> {
                  if (config == null || !config.isEnabled()) {
                      authEndpointSupport.respondError(ctx, 400, "Unknown or disabled system OIDC config: " + pathConfigId);
                      return Future.<String>succeededFuture();
                  }
                  return oidcFlowOrchestrator.startFlow(ctx, config, SESSION_KEYS,
                                                       callbackUrl(config.getId()), null);
              })
              .onSuccess(url -> {
                  if (url != null) {
                      ctx.response().setStatusCode(302).putHeader("Location", url).end();
                  }
              })
              .onFailure(ex -> {
                  log.error("System login start failed for config {}", pathConfigId, ex);
                  authEndpointSupport.respondError(ctx, 500, "Provider initialization failed");
              });
    }

    private void handleCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.<SystemOidcConfiguration>handleCallback(
                ctx, pathConfigId, SESSION_KEYS, callbackUrl(pathConfigId),
                systemOidcConfigurationService::findById)
                .onSuccess(result -> authEndpointSupport.completeOidcLogin(ctx, result.config(), result.claims(),
                        sub -> iamUserService.findByOidcIdentityAndScope(
                                sub, result.config().getId(),
                                AuthScopeType.SYSTEM.name(), OidcConstants.SYSTEM_SCOPE_ID)))
                .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private String callbackUrl(String configId) {
        return authEndpointSupport.absoluteUrl(OidcConstants.SYSTEM_LOGIN_BASE + "/callback/" + configId);
    }
}
