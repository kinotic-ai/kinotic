package org.kinotic.domain.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.KinoticAudience;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.kinotic.domain.api.services.iam.DeviceCodeGrantService;
import org.kinotic.domain.api.services.iam.OAuthAuthorizationService;
import org.kinotic.domain.api.services.iam.RefreshTokenService;
import org.kinotic.domain.internal.api.rest.support.AuthEndpointSupport;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The OAuth 2.1 authorization-server surface MCP hosts discover and drive to reach
 * {@code POST /mcp}: RFC 8414 / RFC 9728 metadata documents, the PKCE authorization-code flow
 * whose consent step is the SPA's {@code /oauth/consent} page, and the RFC 8628 device grant the
 * CLI logs in with. Clients identify themselves with a Client ID Metadata Document URL
 * (draft-ietf-oauth-client-id-metadata-document) rather than registering; there is no registration
 * endpoint. Token responses carry a Kinotic access token plus a rotating refresh token, so clients
 * requesting {@code offline_access} refresh without re-consent.
 *
 * <p>Each grant hands out one surface and only that surface: the authorization-code grant issues
 * {@link KinoticAudience#MCP_TOOLS} tokens, the device grant {@link KinoticAudience#PUBLISHED_SERVICES}
 * tokens, and the refresh grant re-mints whichever audience its lineage was issued for. Every token
 * acts as the user who approved the grant.
 *
 * <p>Error responses use the RFC 6749 shape {@code {"error":"<code>"}}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthServerHandler implements SuppliesGatewayRoutes {

    private static final String DEVICE_CODE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code";

    private final AuthEndpointSupport authEndpointSupport;
    private final OAuthAuthorizationService oauthAuthorizationService;
    private final DeviceCodeGrantService deviceCodeGrantService;
    private final RefreshTokenService refreshTokenService;
    private final KinoticDomainProperties domainProperties;

    @Override
    public void mountRoutes(Router router) {
        router.get("/.well-known/oauth-authorization-server").handler(this::handleAuthorizationServerMetadata);
        router.get("/.well-known/oauth-protected-resource").handler(this::handleProtectedResourceMetadata);
        // RFC 9728 path-inserted form for the /mcp resource
        router.get("/.well-known/oauth-protected-resource/mcp").handler(this::handleProtectedResourceMetadata);
        router.get("/api/auth/oauth/authorize").handler(this::handleAuthorize);
        router.post("/api/auth/oauth/device_authorization").handler(this::handleDeviceAuthorization);
        router.post("/api/auth/oauth/token").handler(this::handleToken);
    }

    /** {@code GET /.well-known/oauth-authorization-server} — RFC 8414 metadata. */
    private void handleAuthorizationServerMetadata(RoutingContext ctx) {
        String issuer = issuer();
        respondJson(ctx, 200, new JsonObject()
                .put("issuer", issuer)
                .put("authorization_endpoint", issuer + "/api/auth/oauth/authorize")
                .put("token_endpoint", issuer + "/api/auth/oauth/token")
                .put("device_authorization_endpoint", issuer + "/api/auth/oauth/device_authorization")
                // draft-ietf-oauth-client-id-metadata-document Section 5 — lets a host check for
                // support before sending the user somewhere that would reject its client_id
                .put("client_id_metadata_document_supported", true)
                .put("response_types_supported", new JsonArray().add("code"))
                .put("grant_types_supported", new JsonArray().add("authorization_code")
                                                             .add("refresh_token")
                                                             .add(DEVICE_CODE_GRANT_TYPE))
                .put("code_challenge_methods_supported", new JsonArray().add("S256"))
                .put("token_endpoint_auth_methods_supported", new JsonArray().add("none"))
                .put("scopes_supported", new JsonArray().add("offline_access")));
    }

    /** {@code GET /.well-known/oauth-protected-resource[/mcp]} — RFC 9728 metadata for {@code /mcp}. */
    private void handleProtectedResourceMetadata(RoutingContext ctx) {
        String issuer = issuer();
        respondJson(ctx, 200, new JsonObject()
                .put("resource", issuer + "/mcp")
                .put("authorization_servers", new JsonArray().add(issuer))
                .put("bearer_methods_supported", new JsonArray().add("header"))
                .put("scopes_supported", new JsonArray().add("offline_access")));
    }

    /**
     * {@code GET /api/auth/oauth/authorize} — validates the request, stores it, and sends the
     * browser to the SPA consent page, which approves or denies over STOMP and then navigates
     * to the client's redirect URI.
     */
    private void handleAuthorize(RoutingContext ctx) {
        String clientId = ctx.request().getParam("client_id");
        String redirectUri = ctx.request().getParam("redirect_uri");
        String responseType = ctx.request().getParam("response_type");
        String codeChallenge = ctx.request().getParam("code_challenge");
        String codeChallengeMethod = ctx.request().getParam("code_challenge_method");
        String scope = ctx.request().getParam("scope");
        String resource = ctx.request().getParam("resource");
        String state = ctx.request().getParam("state");

        // never redirect an invalid request: the redirect_uri is only trustworthy once the
        // client and URI have been validated together, which createAuthorizationRequest does
        if (!"code".equals(responseType)) {
            authEndpointSupport.respondError(ctx, 400, "unsupported_response_type");
            return;
        }
        if (codeChallenge == null || codeChallenge.isBlank() || !"S256".equals(codeChallengeMethod)) {
            authEndpointSupport.respondError(ctx, 400, "invalid_request");
            return;
        }
        Future.fromCompletionStage(
                      oauthAuthorizationService.createAuthorizationRequest(clientId, redirectUri, codeChallenge,
                                                                           scope, resource, state))
              .onSuccess(requestId -> ctx.response()
                                         .setStatusCode(302)
                                         .putHeader("Location", authEndpointSupport.appUrl("/oauth/consent?request_id="
                                                 + URLEncoder.encode(requestId, StandardCharsets.UTF_8)))
                                         .end())
              .onFailure(err -> {
                  log.warn("OAuth authorize request rejected: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 400, "invalid_request");
              });
    }

    /**
     * {@code POST /api/auth/oauth/device_authorization} — RFC 8628 §3.1/§3.2. Device clients
     * are anonymous public clients ({@code client_id} is accepted but not required): a device
     * grant has no redirect URI to protect, its authority is the browser approval on the
     * {@code /device} page.
     */
    private void handleDeviceAuthorization(RoutingContext ctx) {
        Future.fromCompletionStage(deviceCodeGrantService.start())
              .onSuccess(start -> {
                  // /device is a kinotic-frontend SPA route (DeviceVerification.vue), not a gateway
                  // route — hence appBaseUrl (SPA origin), not apiBaseUrl. The signed-in browser
                  // approves there via OAuthApprovalService.approveDevice over STOMP; this gateway only emits the URL.
                  String verificationUri = domainProperties.getDomain().getAppBaseUrl() + "/device";
                  respondJson(ctx, 200, new JsonObject()
                          .put("device_code", start.deviceCode())
                          .put("user_code", start.userCode())
                          .put("verification_uri", verificationUri)
                          .put("verification_uri_complete",
                               verificationUri + "?user_code="
                                       + URLEncoder.encode(start.userCode(), StandardCharsets.UTF_8))
                          .put("expires_in", Math.max((start.expiresAt().getTime() - System.currentTimeMillis()) / 1000L, 0))
                          .put("interval", start.intervalSeconds()));
              })
              .onFailure(err -> {
                  log.warn("Device authorization start failed: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Could not start device authorization");
              });
    }

    /**
     * {@code POST /api/auth/oauth/token} — form-encoded per RFC 6749. Supports the
     * {@code authorization_code} (PKCE), {@code refresh_token}, and RFC 8628 device-code grants.
     */
    private void handleToken(RoutingContext ctx) {
        String grantType = ctx.request().getFormAttribute("grant_type");
        if ("authorization_code".equals(grantType)) {
            handleAuthorizationCodeGrant(ctx);
        } else if ("refresh_token".equals(grantType)) {
            handleRefreshTokenGrant(ctx);
        } else if (DEVICE_CODE_GRANT_TYPE.equals(grantType)) {
            handleDeviceCodeGrant(ctx);
        } else {
            authEndpointSupport.respondError(ctx, 400, "unsupported_grant_type");
        }
    }

    private void handleDeviceCodeGrant(RoutingContext ctx) {
        String deviceCode = ctx.request().getFormAttribute("device_code");
        if (deviceCode == null || deviceCode.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "invalid_request");
            return;
        }
        Future.fromCompletionStage(deviceCodeGrantService.poll(deviceCode))
              .onSuccess(result -> {
                  switch (result.status()) {
                      case AUTHORIZATION_PENDING -> authEndpointSupport.respondError(ctx, 400, "authorization_pending");
                      case SLOW_DOWN -> authEndpointSupport.respondError(ctx, 400, "slow_down");
                      case EXPIRED -> authEndpointSupport.respondError(ctx, 400, "expired_token");
                      case INVALID -> authEndpointSupport.respondError(ctx, 400, "invalid_grant");
                      case APPROVED -> Future.fromCompletionStage(
                              refreshTokenService.issue(result.user().getId(), KinoticAudience.PUBLISHED_SERVICES))
                              .onSuccess(refreshToken -> authEndpointSupport.respondTokenPair(
                                      ctx, result.user(), refreshToken, KinoticAudience.PUBLISHED_SERVICES))
                              .onFailure(err -> {
                                  log.warn("Could not issue refresh token after device approval: {}", err.getMessage());
                                  authEndpointSupport.respondError(ctx, 500, "Could not issue tokens");
                              });
                  }
              })
              .onFailure(err -> {
                  log.warn("Device token poll failed: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 400, "invalid_grant");
              });
    }

    private void handleAuthorizationCodeGrant(RoutingContext ctx) {
        String code = ctx.request().getFormAttribute("code");
        String clientId = ctx.request().getFormAttribute("client_id");
        String redirectUri = ctx.request().getFormAttribute("redirect_uri");
        String codeVerifier = ctx.request().getFormAttribute("code_verifier");
        Future.fromCompletionStage(oauthAuthorizationService.exchangeCode(code, clientId, redirectUri, codeVerifier))
              .compose(user -> Future.fromCompletionStage(
                                             refreshTokenService.issue(user.getId(), KinoticAudience.MCP_TOOLS))
                                     .onSuccess(refreshToken -> authEndpointSupport.respondTokenPair(
                                             ctx, user, refreshToken, KinoticAudience.MCP_TOOLS)))
              .onFailure(err -> {
                  log.warn("OAuth code exchange failed: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 400, "invalid_grant");
              });
    }

    private void handleRefreshTokenGrant(RoutingContext ctx) {
        String refreshToken = ctx.request().getFormAttribute("refresh_token");
        if (refreshToken == null || refreshToken.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "invalid_request");
            return;
        }
        Future.fromCompletionStage(refreshTokenService.rotate(refreshToken))
              .onSuccess(rotation -> authEndpointSupport.respondTokenPair(
                      ctx, rotation.user(), rotation.refreshToken(), rotation.audience()))
              .onFailure(err -> {
                  log.warn("OAuth refresh token rotation failed: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 400, "invalid_grant");
              });
    }

    private String issuer() {
        return authEndpointSupport.absoluteUrl("");
    }

    private static void respondJson(RoutingContext ctx, int status, JsonObject body) {
        ctx.response().setStatusCode(status).putHeader("Content-Type", "application/json").end(body.encode());
    }

}
