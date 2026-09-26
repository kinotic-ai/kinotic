package org.kinotic.domain.api.rest;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.services.security.DeviceCodeGrantService;
import org.kinotic.domain.internal.api.rest.support.AuthEndpointSupport;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The RFC 8628 device authorization endpoint the Kinotic CLI logs in through. The CLI is a
 * pre-registered public client and the only one this endpoint serves. The user approves the
 * device on the SPA's {@code /device} page, and the CLI redeems its device code at the token
 * endpoint of {@link OAuthServerHandler}.
 *
 * <p>Error responses use the RFC 6749 shape {@code {"error":"<code>"}}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAuthorizationHandler implements SuppliesGatewayRoutes {

    static final String DEVICE_AUTHORIZATION_ROUTE = "/api/auth/oauth/device_authorization";

    /**
     * The {@code client_id} of the Kinotic CLI, the only client the device grant serves. Constant
     * rather than configuration: it identifies the CLI itself, not a deployment of it.
     */
    static final String CLI_CLIENT_ID = "kinotic-cli";

    private final AuthEndpointSupport authEndpointSupport;
    private final DeviceCodeGrantService deviceCodeGrantService;

    @Override
    public void mountRoutes(Router router) {
        router.post(DEVICE_AUTHORIZATION_ROUTE).handler(this::handleDeviceAuthorization);
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
        deviceCodeGrantService.start(ctx.request().getFormAttribute("device_name"))
              .onSuccess(start -> {
                  // /device is a kinotic-frontend SPA route (DeviceVerification.vue), not a gateway
                  // route — hence appUrl (SPA origin), not absoluteUrl. The signed-in browser
                  // approves there via OAuthApprovalService.approveDevice over STOMP; this gateway only emits the URL.
                  String verificationUri = authEndpointSupport.appUrl("/device");
                  ctx.json(new JsonObject()
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

}
