package org.kinotic.gateway.internal.endpoints.rest;

import org.kinotic.domain.api.services.iam.SignUpService;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST handler for email/password organization sign-up. Verification comes first: the initial
 * request collects only email + display name and sends the verification link; the organization
 * name and password are collected at completion.
 * <p>
 * Mounts routes on the shared Vert.x Router alongside the STOMP/WebSocket server. Contains no
 * business logic — delegates entirely to {@link SignUpService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignUpHandler {

    private final SignUpService signUpService;

    /**
     * Mounts the sign-up REST routes on the given router.
     * Must be called before the router is passed to the STOMP server factory.
     */
    public void mountRoutes(Router router) {
        router.post("/api/signup").handler(ctx -> {
            try {
                JsonObject body = ctx.body().asJsonObject();
                String email = body.getString("email");
                String displayName = body.getString("displayName");

                signUpService.initiateLocalSignUp(email, displayName)
                        .thenAccept(v -> ctx.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("message", "Verification email sent. Please check your inbox.").encode()))
                        .exceptionally(ex -> {
                            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                            log.warn("Sign-up failed: {}", cause.getMessage());
                            ctx.response()
                               .setStatusCode(400)
                               .putHeader("Content-Type", "application/json")
                               .end(new JsonObject().put("error", cause.getMessage()).encode());
                            return null;
                        });
            } catch (Exception e) {
                log.error("Failed to parse sign-up request", e);
                ctx.response()
                   .setStatusCode(400)
                   .putHeader("Content-Type", "application/json")
                   .end(new JsonObject().put("error", "Invalid request body").encode());
            }
        });

        router.post("/api/signup/complete").handler(ctx -> {
            try {
                JsonObject body = ctx.body().asJsonObject();
                String token = body.getString("token");
                String orgName = body.getString("orgName");
                String orgDescription = body.getString("orgDescription");
                String password = body.getString("password");

                signUpService.completeLocalSignUp(token, orgName, orgDescription, password)
                        .thenAccept(orgId -> ctx.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject()
                                        .put("message", "Account created successfully")
                                        .put("orgId", orgId)
                                        .encode()))
                        .exceptionally(ex -> {
                            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                            log.warn("Sign-up completion failed: {}", cause.getMessage());
                            ctx.response()
                               .setStatusCode(400)
                               .putHeader("Content-Type", "application/json")
                               .end(new JsonObject().put("error", cause.getMessage()).encode());
                            return null;
                        });
            } catch (Exception e) {
                log.error("Failed to parse sign-up completion request", e);
                ctx.response()
                   .setStatusCode(400)
                   .putHeader("Content-Type", "application/json")
                   .end(new JsonObject().put("error", "Invalid request body").encode());
            }
        });
    }
}
