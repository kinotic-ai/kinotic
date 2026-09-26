package org.kinotic.domain.api.rest;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.security.DelegateKind;
import org.kinotic.domain.api.services.security.DeviceCodeGrantService;
import org.kinotic.domain.internal.api.rest.support.AuthEndpointSupport;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The RFC 8628 device grant the Kinotic CLI logs in through: the device authorization endpoint, the
 * redemption of its device codes at the token endpoint of {@link OAuthServerHandler}, and the entries
 * advertising both in that handler's RFC 8414 metadata. The CLI is a pre-registered public client and
 * the only one the grant serves; the user approves the device on the SPA's {@code /device} page.
 *
 * <p>A server serves the device grant by importing this class with
 * {@code @Import(DeviceAuthorizationHandler.class)}.
 *
 * <p>Error responses use the RFC 6749 shape {@code {"error":"<code>"}}.
 */
// Registered only by @Import, so the device grant exists on the servers that import it and no others
@Slf4j
@RequiredArgsConstructor
public class DeviceAuthorizationHandler implements SuppliesGatewayRoutes {

    static final String DEVICE_CODE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code";

    private static final String DEVICE_AUTHORIZATION_ROUTE = "/api/auth/oauth/device_authorization";

    /**
     * The {@code client_id} of the Kinotic CLI, the only client the device grant serves. Constant
     * rather than configuration: it identifies the CLI itself, not a deployment of it.
     */
    private static final String CLI_CLIENT_ID = "kinotic-cli";

    /** Display name of the CLI's delegate wherever the user's authorized clients are listed. */
    private static final String CLI_DISPLAY_NAME = "Kinotic CLI";

    private final AuthEndpointSupport authEndpointSupport;
    private final ServerSurface serverSurface;
    private final DeviceCodeGrantService deviceCodeGrantService;

    @Override
    public void mountRoutes(Router router) {
        router.post(DEVICE_AUTHORIZATION_ROUTE).handler(this::handleDeviceAuthorization);
    }

    /**
     * Adds the device grant to RFC 8414 authorization-server metadata: the device authorization
     * endpoint under {@code issuer}, and the device-code grant type in {@code grant_types_supported}.
     */
    void advertise(JsonObject metadata, String issuer) {
        metadata.put("device_authorization_endpoint", issuer + DEVICE_AUTHORIZATION_ROUTE);
        metadata.getJsonArray("grant_types_supported").add(DEVICE_CODE_GRANT_TYPE);
    }

    /**
     * Answers a token request carrying the device-code grant — RFC 8628 §3.4/§3.5: the RFC's error
     * for the code's state ({@code authorization_pending}, {@code slow_down}, {@code expired_token},
     * {@code invalid_grant}), or, once the user has approved the code, a token pair acting as that user.
     */
    void redeem(RoutingContext ctx) {
        String deviceCode = ctx.request().getFormAttribute("device_code");
        if (deviceCode == null || deviceCode.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "invalid_request");
            return;
        }
        deviceCodeGrantService.poll(serverSurface.issuerBaseUrl(ctx), deviceCode)
              .onSuccess(result -> {
                  switch (result.status()) {
                      case AUTHORIZATION_PENDING -> authEndpointSupport.respondError(ctx, 400, "authorization_pending");
                      case SLOW_DOWN -> authEndpointSupport.respondError(ctx, 400, "slow_down");
                      case EXPIRED -> authEndpointSupport.respondError(ctx, 400, "expired_token");
                      case INVALID -> authEndpointSupport.respondError(ctx, 400, "invalid_grant");
                      case APPROVED -> authEndpointSupport.issueDelegateTokens(ctx, result.user(), DelegateKind.CLI,
                                                                              CLI_CLIENT_ID, CLI_DISPLAY_NAME,
                                                                              result.deviceName())
                              .onFailure(err -> {
                                  log.warn("Could not issue tokens after device approval: {}", err.getMessage());
                                  authEndpointSupport.respondError(ctx, 500, "Could not issue tokens");
                              });
                  }
              })
              .onFailure(err -> {
                  log.warn("Device token poll failed: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 400, "invalid_grant");
              });
    }

    /**
     * {@code POST /api/auth/oauth/device_authorization} — RFC 8628 §3.1/§3.2. Serves the Kinotic
     * CLI, a pre-registered public client, so {@code client_id} is required and must name it. A
     * device grant has no redirect URI to protect; its authority is the browser approval on the
     * {@code /device} page.
     */
    private void handleDeviceAuthorization(RoutingContext ctx) {
        if (!CLI_CLIENT_ID.equals(ctx.request().getFormAttribute("client_id"))) {
            authEndpointSupport.respondError(ctx, 400, "invalid_client");
            return;
        }
        deviceCodeGrantService.start(serverSurface.issuerBaseUrl(ctx), ctx.request().getFormAttribute("device_name"))
              // /device is a kinotic-frontend SPA route (DeviceVerification.vue), not a gateway
              // route — hence the UI's URL, not the API's. The signed-in browser approves there via
              // OAuthApprovalService.approveDevice over STOMP; this gateway only emits the URL.
              .compose(start -> serverSurface.uiUrl(ctx, "/device").map(verificationUri -> new JsonObject()
                      .put("device_code", start.deviceCode())
                      .put("user_code", start.userCode())
                      .put("verification_uri", verificationUri)
                      .put("verification_uri_complete",
                           verificationUri + "?user_code="
                                   + URLEncoder.encode(start.userCode(), StandardCharsets.UTF_8))
                      .put("expires_in", Math.max((start.expiresAt().getTime() - System.currentTimeMillis()) / 1000L, 0))
                      .put("interval", start.intervalSeconds())))
              .onSuccess(ctx::json)
              .onFailure(err -> {
                  log.warn("Device authorization start failed: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Could not start device authorization");
              });
    }

}
