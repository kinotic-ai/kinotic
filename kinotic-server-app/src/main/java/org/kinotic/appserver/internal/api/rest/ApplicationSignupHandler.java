package org.kinotic.appserver.internal.api.rest;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.kinotic.domain.api.services.security.SignUpService;
import org.springframework.stereotype.Component;

/**
 * An application's sign-up endpoints, for a new customer signing up on the application's own pages: a
 * verification email first, then the tenant, its first user and the session once the link is followed. Served
 * only by an application that offers {@code TENANT_SIGN_UP}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationSignupHandler implements SuppliesGatewayRoutes {

    private final SignUpService signUpService;
    private final ApplicationAuthEndpointSupport authEndpointSupport;

    @Override
    public void mountRoutes(Router router) {
        router.post("/api/auth/app/signup").handler(this::handleSignUp);
        router.post("/api/auth/app/signup/complete").handler(this::handleComplete);
    }

    /**
     * {@code POST /api/auth/app/signup {email, displayName}} — starts a customer's sign-up to the application the
     * request is addressed to: stores a pending sign-up and emails a verification link into the application's
     * primary UI. The tenant's name and the password are collected at {@link #handleComplete}.
     */
    private void handleSignUp(RoutingContext ctx) {
        ApplicationKey applicationKey = authEndpointSupport.applicationKey(ctx);
        JsonObject body = authEndpointSupport.readJsonBody(ctx);
        signUpService.initiateTenantSignUp(applicationKey, body.getString("email"), body.getString("displayName"))
                     .onSuccess(v -> ctx.response()
                                        .setStatusCode(200)
                                        .putHeader("Content-Type", "application/json")
                                        .end(new JsonObject().put("message", "Verification email sent. Please check your inbox.").encode()))
                     .onFailure(ex -> {
                         log.warn("Tenant sign-up failed for {}: {}", applicationKey, ex.getMessage());
                         authEndpointSupport.respondError(ctx, 400, ex.getMessage());
                     });
    }

    /**
     * {@code POST /api/auth/app/signup/complete {token, tenantName, password}} — finishes a customer's sign-up
     * once the verification link was followed: creates the tenant, the customer as its first user and
     * administrator, and the password credential, then establishes the browser session so the customer is
     * signed in to the application.
     */
    private void handleComplete(RoutingContext ctx) {
        JsonObject body = authEndpointSupport.readJsonBody(ctx);
        signUpService.completeTenantSignUp(body.getString("token"), body.getString("tenantName"), body.getString("password"))
                     .onSuccess(user -> authEndpointSupport.respondSuccess(ctx, user))
                     .onFailure(ex -> {
                         log.warn("Tenant sign-up completion failed: {}", ex.getMessage());
                         authEndpointSupport.respondError(ctx, 400, ex.getMessage());
                     });
    }
}
