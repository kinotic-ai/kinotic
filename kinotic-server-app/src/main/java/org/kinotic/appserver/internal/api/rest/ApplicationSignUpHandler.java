package org.kinotic.appserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.domain.api.exceptions.ExistingUserException;
import org.kinotic.domain.api.model.security.LoginScope;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.services.security.ApplicationSignUpService;
import org.kinotic.domain.api.utils.HostLabelUtil;
import org.springframework.stereotype.Component;

/**
 * Sign-up of new users of an application with an email and a password, served at the application's own API host
 * from pages that are the application's UIs, as its login is. A page asks for the email and sends it; the person
 * follows the link emailed to it to the same UI's {@code /signup/verify} page, which sends the token with the password
 * the person chooses and is signed in. The start answers every email alike, so it tells nothing about which emails
 * have a user. Sign-up is to the application's own login only: a tenant's users join through invitations and the
 * tenant's own providers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationSignUpHandler implements SuppliesGatewayRoutes {

    private final ApplicationSignUpService signUpService;
    private final ApplicationAuthEndpointSupport authEndpointSupport;

    @Override
    public void mountRoutes(Router router) {
        router.post("/api/auth/app/signup").handler(this::handleSignUp);
        router.post("/api/auth/app/signup/complete").handler(this::handleComplete);
    }

    /**
     * {@code POST /api/auth/app/signup {email[, displayName]}} — emails {@code email} a link to the
     * {@code /signup/verify?token=…} page of the UI that sent the request, and answers {@code 202} whether or not the
     * email has a user, before anything is sent; an email that has one is sent nothing, and so is one whose link from
     * an earlier request is still valid. {@code 403} from a page that is not one of the application's UIs, from a
     * tenant's site, and while the application does not offer sign-up with an email and a password; {@code 400} for
     * an email with no {@code @} and from a request no page sent; {@code 404} for an application that does not exist.
     */
    private void handleSignUp(RoutingContext ctx) {
        String origin = SessionBinding.origin(ctx);
        if (origin == null) {
            authEndpointSupport.respondError(ctx, 400, "A sign-up is started by a page");
            return;
        }
        JsonObject body = authEndpointSupport.readJsonBody(ctx);
        String email = body.getString("email");
        String displayName = body.getString("displayName");

        authEndpointSupport.loginScope(ctx, null)
              .onSuccess(scope -> startSignUp(ctx, scope, email, displayName, origin))
              .onFailure(err -> authEndpointSupport.fail(ctx, err, "Failed to start sign-up"));
    }

    private void startSignUp(RoutingContext ctx, LoginScope scope, String email, String displayName, String origin) {
        Future<Void> started;
        try {
            started = signUpService.initiatePasswordSignUp(scope, email, displayName, origin);
        } catch (IllegalStateException e) {
            authEndpointSupport.respondError(ctx, 403, e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            authEndpointSupport.respondError(ctx, 400, e.getMessage());
            return;
        }
        // answered once the request is checked and before the email is looked up, so neither the answer nor its timing
        // depends on whether the email has a user
        ctx.response().setStatusCode(202).end();
        started.onFailure(err -> log.warn("Sign-up to {} could not be started: {}", HostLabelUtil.label(scope.applicationKey()), err.getMessage()));
    }

    /**
     * {@code POST /api/auth/app/signup/complete {token, password}} — completes the sign-up the token was emailed
     * for, creating the application's user with the verified email and the password, and answers {@code 204} with the
     * session cookie, signed in on the page that sent it. {@code 400} for a token that is unknown, spent, expired or
     * for another application, or a missing password; {@code 409} when the email has a user already, which the person
     * signs in as instead; {@code 403} from a page that is not one of the application's UIs, from a tenant's site, or
     * once the application no longer offers sign-up; {@code 404} for an application that does not exist.
     */
    private void handleComplete(RoutingContext ctx) {
        if (SessionBinding.origin(ctx) == null) {
            authEndpointSupport.respondError(ctx, 400, "A sign-up is completed by a page");
            return;
        }
        JsonObject body = authEndpointSupport.readJsonBody(ctx);
        String token = body.getString("token");
        String password = body.getString("password");

        authEndpointSupport.loginScope(ctx, null)
              .compose(scope -> signUpService.completePasswordSignUp(scope, token, password))
              .onSuccess(user -> authEndpointSupport.respondSuccess(ctx, AuthEndpointSupport.participantOf(user)))
              .onFailure(err -> {
                  switch (err) {
                      // the person has shown the email is theirs, so telling them it has a user reveals nothing to anyone else
                      case ExistingUserException _ -> authEndpointSupport.respondError(ctx, 409, "This email already has an account; sign in instead");
                      case IllegalStateException _ -> authEndpointSupport.respondError(ctx, 403, err.getMessage());
                      case IllegalArgumentException _ -> authEndpointSupport.respondError(ctx, 400, err.getMessage());
                      default -> authEndpointSupport.fail(ctx, err, "Failed to complete sign-up");
                  }
              });
    }
}
