package org.kinotic.domain.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.web.Router;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.iam.DeviceCodePollResult;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.services.iam.DeviceCodeGrantService;
import org.kinotic.domain.api.services.iam.RefreshTokenService;
import org.kinotic.domain.internal.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.internal.api.services.iam.KinoticJwtIssuer;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * CLI authentication routes implementing the OAuth 2.0 Device Authorization Grant
 * (RFC 8628) plus refresh-token rotation. The CLI is a public client: it holds no secret,
 * drives the user through a browser login, and afterwards keeps only a rotating refresh
 * token from which it mints the short-lived access tokens used on STOMP CONNECT.
 *
 * <p>Error responses use the RFC 8628 / RFC 6749 shape {@code {"error":"<code>"}} so the CLI
 * can branch on the code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CliDeviceLoginHandler implements SuppliesGatewayRoutes {

    /** Access-token TTL for the CLI's short-lived JWT. */
    private static final int JWT_TTL_SECONDS = 60;

    private final AuthEndpointSupport authEndpointSupport;
    private final DeviceCodeGrantService deviceCodeGrantService;
    private final RefreshTokenService refreshTokenService;
    private final KinoticDomainProperties domainProperties;
    private final KinoticJwtIssuer jwtIssuer;

    @Override
    public void mountRoutes(Router router) {
        router.post("/api/auth/device/start").handler(this::handleStart);
        router.post("/api/auth/device/token").handler(this::handleToken);
        router.post("/api/auth/device/refresh").handler(this::handleRefresh);
    }

    /**
     * {@code POST /api/auth/device/start} — begins a flow; returns the device/user codes
     * and the browser verification URL.
     */
    private void handleStart(RoutingContext ctx) {
        Future.fromCompletionStage(deviceCodeGrantService.start())
              .onSuccess(start -> {
                  // /device is a kinotic-frontend SPA route (DeviceVerification.vue), not a gateway
                  // route — hence appBaseUrl (SPA origin), not apiBaseUrl. The signed-in browser
                  // approves there via DeviceApprovalService over STOMP; this gateway only emits the URL.
                  String verificationUri = domainProperties.getDomain().getAppBaseUrl() + "/device";
                  JsonObject body = new JsonObject()
                          .put("device_code", start.deviceCode())
                          .put("user_code", start.userCode())
                          .put("verification_uri", verificationUri)
                          .put("verification_uri_complete",
                               verificationUri + "?user_code="
                                       + URLEncoder.encode(start.userCode(), StandardCharsets.UTF_8))
                          .put("expires_in", secondsUntil(start.expiresAt()))
                          .put("interval", start.intervalSeconds());
                  ctx.response().putHeader("Content-Type", "application/json").end(body.encode());
              })
              .onFailure(err -> {
                  log.warn("Device authorization start failed: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Could not start device authorization");
              });
    }

    /**
     * {@code POST /api/auth/device/token} — polled by the CLI; returns
     * {@code authorization_pending} until approved, then an access/refresh token pair.
     */
    private void handleToken(RoutingContext ctx) {
        String deviceCode = stringField(ctx, "device_code");
        if (deviceCode == null) {
            authEndpointSupport.respondError(ctx, 400, "device_code is required");
            return;
        }
        Future.fromCompletionStage(deviceCodeGrantService.poll(deviceCode))
              .onSuccess(result -> {
                  switch (result.status()) {
                      case AUTHORIZATION_PENDING -> authEndpointSupport.respondError(ctx, 400, "authorization_pending");
                      case SLOW_DOWN -> authEndpointSupport.respondError(ctx, 400, "slow_down");
                      case EXPIRED -> authEndpointSupport.respondError(ctx, 400, "expired_token");
                      case INVALID -> authEndpointSupport.respondError(ctx, 400, "invalid_grant");
                      case APPROVED -> issueTokensForApprovedGrant(ctx, result);
                  }
              })
              .onFailure(err -> {
                  log.warn("Device token poll failed: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 400, "invalid_grant");
              });
    }

    private void issueTokensForApprovedGrant(RoutingContext ctx, DeviceCodePollResult result) {
        Future.fromCompletionStage(refreshTokenService.issue(result.user().getId()))
              .onSuccess(refreshToken -> respondTokenPair(ctx, result.user(), refreshToken))
              .onFailure(err -> {
                  log.warn("Could not issue refresh token after device approval: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Could not issue tokens");
              });
    }

    /**
     * {@code POST /api/auth/device/refresh} — exchanges a refresh token for a new
     * access/refresh token pair (rotation).
     */
    private void handleRefresh(RoutingContext ctx) {
        String refreshToken = stringField(ctx, "refresh_token");
        if (refreshToken == null) {
            authEndpointSupport.respondError(ctx, 400, "refresh_token is required");
            return;
        }
        Future.fromCompletionStage(refreshTokenService.rotate(refreshToken))
              .onSuccess(rotation -> respondTokenPair(ctx, rotation.user(), rotation.refreshToken()))
              .onFailure(err -> {
                  log.warn("Refresh token rotation failed: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 400, "invalid_grant");
              });
    }

    /** Reads a non-blank string field from the JSON body, or {@code null} if absent/blank/unparseable. */
    private String stringField(RoutingContext ctx, String field) {
        JsonObject body;
        try {
            body = ctx.body().asJsonObject();
        } catch (Exception e) {
            return null;
        }
        if (body == null) {
            return null;
        }
        String value = body.getString(field);
        return (value == null || value.isBlank()) ? null : value;
    }

    private long secondsUntil(Date when) {
        return Math.max((when.getTime() - System.currentTimeMillis()) / 1000L, 0);
    }

    /** Mints the short-TTL Kinotic JWT carrying {@code sub/email/organizationId/applicationId}. */
    private String mintJwt(IamUser user) {
        JsonObject claims = new JsonObject()
                .put("sub", user.getId())
                .put("email", user.getEmail());
        if (user.getOrganizationId() != null) {
            claims.put("organizationId", user.getOrganizationId());
        }
        if (user.getApplicationId() != null) {
            claims.put("applicationId", user.getApplicationId());
        }
        return jwtIssuer.sign(claims, new JWTOptions().setExpiresInSeconds(JWT_TTL_SECONDS));
    }

    /**
     * {@code 200 application/json} with the OAuth token-pair the CLI consumes: a short-TTL
     * {@code access_token} plus the {@code refresh_token} the client persists to mint future
     * access tokens.
     */
    private void respondTokenPair(RoutingContext ctx, IamUser user, String refreshToken) {
        JsonObject body = new JsonObject()
                .put("access_token", mintJwt(user))
                .put("token_type", "Bearer")
                .put("expires_in", JWT_TTL_SECONDS)
                .put("refresh_token", refreshToken);
        ctx.response().putHeader("Content-Type", "application/json").end(body.encode());
    }
}
