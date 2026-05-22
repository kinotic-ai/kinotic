package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.services.iam.DeviceCodeGrantService;
import org.kinotic.domain.api.services.iam.DeviceCodeGrantService.DeviceCodePollResult;
import org.kinotic.domain.api.services.iam.RefreshTokenService;
import org.kinotic.gateway.api.config.KinoticApiGatewayProperties;
import org.kinotic.gateway.internal.endpoints.rest.support.AuthEndpointSupport;
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
 * <ul>
 *   <li>{@code POST /api/login/device/start} — begins a flow; returns the device/user codes
 *       and the browser verification URL.</li>
 *   <li>{@code POST /api/login/device/token} — polled by the CLI; returns
 *       {@code authorization_pending} until approved, then an access/refresh token pair.</li>
 *   <li>{@code POST /api/login/device/approve} — called by the authenticated browser to bind
 *       the logged-in user to a {@code user_code}.</li>
 *   <li>{@code POST /api/login/device/refresh} — exchanges a refresh token for a new
 *       access/refresh token pair (rotation).</li>
 * </ul>
 *
 * <p>Error responses use the RFC 8628 / RFC 6749 shape {@code {"error":"<code>"}} so the CLI
 * can branch on the code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CliDeviceLoginHandler {

    private final AuthEndpointSupport authEndpointSupport;
    private final DeviceCodeGrantService deviceCodeGrantService;
    private final RefreshTokenService refreshTokenService;
    private final KinoticApiGatewayProperties gatewayProperties;

    public void mountRoutes(Router router) {
        router.post(OidcConstants.DEVICE_LOGIN_BASE + "/start").handler(this::handleStart);
        router.post(OidcConstants.DEVICE_LOGIN_BASE + "/token").handler(this::handleToken);
        router.post(OidcConstants.DEVICE_LOGIN_BASE + "/approve").handler(this::handleApprove);
        router.post(OidcConstants.DEVICE_LOGIN_BASE + "/refresh").handler(this::handleRefresh);
    }

    private void handleStart(RoutingContext ctx) {
        Future.fromCompletionStage(deviceCodeGrantService.start())
              .onSuccess(start -> {
                  String verificationUri = gatewayProperties.getAppBaseUrl() + OidcConstants.DEVICE_VERIFICATION_PATH;
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
              .onSuccess(issued -> authEndpointSupport.respondTokenPair(ctx, result.user(), issued.token()))
              .onFailure(err -> {
                  log.warn("Could not issue refresh token after device approval: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Could not issue tokens");
              });
    }

    private void handleApprove(RoutingContext ctx) {
        String userCode = stringField(ctx, "user_code");
        if (userCode == null) {
            authEndpointSupport.respondError(ctx, 400, "user_code is required");
            return;
        }
        authEndpointSupport.requireAuthenticatedUserId(ctx)
              .onFailure(err -> authEndpointSupport.respondError(ctx, 401, "Authentication required"))
              .onSuccess(userId ->
                  Future.fromCompletionStage(deviceCodeGrantService.approve(userCode, userId))
                        .onSuccess(v -> ctx.response().putHeader("Content-Type", "application/json")
                                           .end(new JsonObject().put("status", "approved").encode()))
                        .onFailure(err -> authEndpointSupport.respondError(ctx, 400, err.getMessage())));
    }

    private void handleRefresh(RoutingContext ctx) {
        String refreshToken = stringField(ctx, "refresh_token");
        if (refreshToken == null) {
            authEndpointSupport.respondError(ctx, 400, "refresh_token is required");
            return;
        }
        Future.fromCompletionStage(refreshTokenService.rotate(refreshToken))
              .onSuccess(rotation -> authEndpointSupport.respondTokenPair(ctx,
                                                                          rotation.user(),
                                                                          rotation.issued().token()))
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
}
