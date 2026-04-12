package org.kinotic.rpc.gateway.internal.endpoints.rest;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.model.iam.SignUpRequest;
import org.kinotic.os.api.services.iam.SignUpService;
import org.springframework.stereotype.Component;

/**
 * REST handler for organization sign-up endpoints.
 * Mounts routes on the shared Vert.x Router alongside the STOMP/WebSocket server.
 * Contains no business logic — delegates entirely to {@link SignUpService}.
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
        // Enable body parsing for /api routes
        router.route("/api/*").handler(BodyHandler.create().setBodyLimit(16384));

        router.post("/api/signup").handler(ctx -> {
            try {
                JsonObject body = ctx.body().asJsonObject();
                SignUpRequest request = new SignUpRequest()
                        .setOrgName(body.getString("orgName"))
                        .setOrgDescription(body.getString("orgDescription"))
                        .setEmail(body.getString("email"))
                        .setDisplayName(body.getString("displayName"));

                signUpService.initiateSignUp(request)
                        .thenAccept(v -> {
                            ctx.response()
                               .setStatusCode(200)
                               .putHeader("Content-Type", "application/json")
                               .end(new JsonObject().put("message", "Verification email sent. Please check your inbox.").encode());
                        })
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
                String password = body.getString("password");

                signUpService.completeSignUp(token, password)
                        .thenAccept(orgId -> {
                            ctx.response()
                               .setStatusCode(200)
                               .putHeader("Content-Type", "application/json")
                               .end(new JsonObject()
                                       .put("message", "Account created successfully")
                                       .put("orgId", orgId)
                                       .encode());
                        })
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
