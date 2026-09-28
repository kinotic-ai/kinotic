package org.kinotic.domain.api.rest;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.security.DelegateKind;
import org.kinotic.domain.api.model.security.KinoticAudience;
import org.kinotic.domain.api.services.security.OAuthAuthorizationService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.kinotic.domain.internal.api.rest.support.AuthEndpointSupport;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The OAuth 2.1 authorization server MCP hosts discover and drive to reach {@code POST /mcp}: its
 * RFC 8414 metadata document, the PKCE authorization-code flow whose consent step is the SPA's
 * {@code /oauth/consent} page, and the token endpoint. On a server that imports
 * {@link DeviceAuthorizationHandler}, the token endpoint also redeems the RFC 8628 device codes it
 * issues to the CLI, and the metadata advertises the device grant. There is no registration
 * endpoint: an MCP host identifies itself with a Client ID Metadata Document URL
 * (draft-ietf-oauth-client-id-metadata-document) the authorize endpoint fetches. Token responses
 * carry a Kinotic access token plus a rotating refresh token, so clients requesting
 * {@code offline_access} refresh without re-consent.
 *
 * <p>Each grant stamps the audience of the surface it serves: the authorization-code grant issues
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

    private final AuthEndpointSupport authEndpointSupport;
    private final OAuthAuthorizationService oauthAuthorizationService;
    private final RefreshTokenService refreshTokenService;
    // present only on a server that imports DeviceAuthorizationHandler
    private final Optional<DeviceAuthorizationHandler> deviceAuthorization;

    @Override
    public void mountRoutes(Router router) {
        router.get("/.well-known/oauth-authorization-server").handler(this::handleAuthorizationServerMetadata);
        router.get("/api/auth/oauth/authorize").handler(this::handleAuthorize);
        router.post("/api/auth/oauth/token").handler(this::handleToken);
    }

    /** {@code GET /.well-known/oauth-authorization-server} — RFC 8414 metadata. */
    private void handleAuthorizationServerMetadata(RoutingContext ctx) {
        String issuer = issuer();
        JsonObject metadata = new JsonObject()
                .put("issuer", issuer)
                .put("authorization_endpoint", issuer + "/api/auth/oauth/authorize")
                .put("token_endpoint", issuer + "/api/auth/oauth/token")
                // draft-ietf-oauth-client-id-metadata-document Section 5 — lets a host check for
                // support before sending the user somewhere that would reject its client_id
                .put("client_id_metadata_document_supported", true)
                .put("response_types_supported", new JsonArray().add("code"))
                .put("grant_types_supported", new JsonArray().add("authorization_code")
                                                             .add("refresh_token"))
                .put("code_challenge_methods_supported", new JsonArray().add("S256"))
                .put("token_endpoint_auth_methods_supported", new JsonArray().add("none"))
                .put("scopes_supported", new JsonArray().add("offline_access"));
        deviceAuthorization.ifPresent(device -> device.advertise(metadata, issuer));
        ctx.json(metadata);
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
        oauthAuthorizationService.createAuthorizationRequest(clientId, redirectUri, codeChallenge,
                                                             scope, resource, state)
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
     * {@code POST /api/auth/oauth/token} — form-encoded per RFC 6749. Supports the
     * {@code authorization_code} (PKCE) and {@code refresh_token} grants, plus the RFC 8628
     * device-code grant on a server that imports {@link DeviceAuthorizationHandler}.
     */
    private void handleToken(RoutingContext ctx) {
        String grantType = ctx.request().getFormAttribute("grant_type");
        if ("authorization_code".equals(grantType)) {
            handleAuthorizationCodeGrant(ctx);
        } else if ("refresh_token".equals(grantType)) {
            handleRefreshTokenGrant(ctx);
        } else if (DeviceAuthorizationHandler.DEVICE_CODE_GRANT_TYPE.equals(grantType) && deviceAuthorization.isPresent()) {
            deviceAuthorization.get().redeem(ctx);
        } else {
            authEndpointSupport.respondError(ctx, 400, "unsupported_grant_type");
        }
    }

    private void handleAuthorizationCodeGrant(RoutingContext ctx) {
        String code = ctx.request().getFormAttribute("code");
        String clientId = ctx.request().getFormAttribute("client_id");
        String redirectUri = ctx.request().getFormAttribute("redirect_uri");
        String codeVerifier = ctx.request().getFormAttribute("code_verifier");
        oauthAuthorizationService.exchangeCode(code, clientId, redirectUri, codeVerifier)
              .compose(exchange -> authEndpointSupport.issueDelegateTokens(ctx, exchange.approver(),
                                                                           DelegateKind.MCP_CLIENT,
                                                                           exchange.clientId(),
                                                                           exchange.clientName(), null))
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
        refreshTokenService.rotate(refreshToken)
              .onSuccess(rotation -> authEndpointSupport.respondTokenPair(
                      ctx, rotation.identity(), rotation.refreshToken(), rotation.audience()))
              .onFailure(err -> {
                  log.warn("OAuth refresh token rotation failed: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 400, "invalid_grant");
              });
    }

    // FIXME: shotgun surgery — one of five places that know the OAuth surface has its own base URL.
    // See "OAuth base URL split" in docs/NavidNotes.md for the topologies that would remove it.
    private String issuer() {
        return authEndpointSupport.issuerUrl("");
    }

}
