package org.kinotic.common.api.oidc;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The sign-ins a server runs through its registrations at providers, with the authorization code flow and PKCE. A
 * sign-in is kept in the browser's session between {@link #start}, which yields the provider's URL to send the
 * browser to, and the provider's callback, which {@link #consume} checks and {@link #verifiedClaims} redeems for the
 * person's claims. The caller finds the {@link OidcClient} of the registration a sign-in goes through.
 */
@Slf4j
@RequiredArgsConstructor
public class OidcSignInFlow {

    // the form of an OAuth error code (RFC 6749 §4.1.2.1)
    private static final Pattern OAUTH_ERROR_CODE = Pattern.compile("[a-z0-9_]{1,64}");

    /** The session key a sign-in is kept under until its callback. */
    private final String sessionKey;

    /**
     * Starts the sign-in {@code state}: keeps it in the session, replacing any the session held, and returns the
     * provider's authorization URL to redirect to. The session's id changes.
     *
     * @param client      the client of the registration the sign-in goes through
     * @param callbackUrl the URL the provider redirects back to
     * @param scopes      the scopes to request
     */
    public String start(RoutingContext ctx, OidcClient client, String callbackUrl, List<String> scopes, OidcFlowState state) {
        String ret = client.authorizationUrl(state.authorization(), callbackUrl, scopes);
        Session session = ctx.session();
        session.regenerateId();
        session.put(sessionKey, state);
        return ret;
    }

    /** The sign-in the session holds for a callback yet to be checked, or {@code null} when it holds none. */
    public OidcFlowState pending(RoutingContext ctx) {
        return ctx.session().get(sessionKey);
    }

    /**
     * Checks the provider's callback against the sign-in the session holds and returns that sign-in, which the
     * session holds no longer whatever the outcome, so a callback cannot be replayed.
     *
     * @throws OidcCallbackException with the code the provider reported in place of a code, such as
     *                               {@code access_denied}; {@link OidcErrorCodes#INVALID_CALLBACK} when the callback
     *                               lacks a code or state; or {@link OidcErrorCodes#STATE_MISMATCH} when it matches
     *                               no sign-in the session started
     */
    public OidcFlowState consume(RoutingContext ctx) {
        String code = ctx.request().getParam("code");
        String state = ctx.request().getParam("state");
        String providerError = ctx.request().getParam("error");
        OidcFlowState ret = ctx.session().remove(sessionKey);

        if (providerError != null) {
            // anyone can address the callback, so only a value in the form of an OAuth error code is carried on
            throw new OidcCallbackException(OAUTH_ERROR_CODE.matcher(providerError).matches() ? providerError
                                                                                              : OidcErrorCodes.INVALID_CALLBACK);
        }
        if (code == null || state == null) {
            throw new OidcCallbackException(OidcErrorCodes.INVALID_CALLBACK);
        }
        if (ret == null || !ret.authorization().state().equals(state)) {
            throw new OidcCallbackException(OidcErrorCodes.STATE_MISMATCH);
        }
        return ret;
    }

    /**
     * Redeems the code of a callback {@link #consume} accepted for the claims of the person who signed in.
     *
     * @param client      the client of the registration {@code state} names
     * @param callbackUrl the URL the sign-in was started with
     * @param state       the sign-in {@link #consume} returned
     * @return the claims, or a failure: an {@link OidcCallbackException} with {@link OidcErrorCodes#INVALID_TOKEN}
     *         when the id token fails verification, and the provider client's own failure otherwise
     */
    public Future<Map<String, Object>> verifiedClaims(RoutingContext ctx, OidcClient client, String callbackUrl, OidcFlowState state) {
        return client.verifiedClaims(ctx.request().getParam("code"), callbackUrl, state.authorization())
                     .recover(err -> {
                         Throwable failure = err;
                         if (err instanceof InvalidIdTokenException) {
                             log.warn("The id token of a sign-in through {} was rejected: {}", state.registrationId(), err.getMessage());
                             failure = new OidcCallbackException(OidcErrorCodes.INVALID_TOKEN);
                         }
                         return Future.failedFuture(failure);
                     });
    }
}
