package org.kinotic.gateway.internal.endpoints.rest.support;

import io.vertx.core.Vertx;
import io.vertx.core.http.CookieSameSite;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Shared primitives for browser redirect flows that bounce the user out to a third-party
 * service (OIDC IdP, GitHub App install) and back. Centralises the parts that were
 * previously duplicated across {@code OidcSignupHandler}, {@code LoginHandler}, and now
 * the GitHub install handlers — the clustered session config, URL-safe random
 * generation for state/PKCE, and the validate-and-consume pattern on the callback.
 */
public final class RedirectFlowSessionSupport {

    /** 10 minutes — covers the slowest IdP / GitHub App roundtrip in practice. */
    public static final long DEFAULT_SESSION_TIMEOUT_MS = 10 * 60 * 1000L;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RedirectFlowSessionSupport() {}

    /**
     * Builds the standard Vert.x {@link SessionHandler} used by all redirect flows.
     * Uses the Ignite-backed {@link ClusteredSessionStore} so the IdP roundtrip can land
     * on any node. {@code SameSite=Lax} so the session cookie is included on the IdP's
     * top-level redirect back to our callback URL.
     */
    public static SessionHandler newSessionHandler(Vertx vertx) {
        SessionStore store = ClusteredSessionStore.create(vertx);
        return SessionHandler.create(store)
                .setCookieHttpOnlyFlag(true)
                .setCookieSecureFlag(true)
                .setCookieSameSite(CookieSameSite.LAX)
                .setSessionTimeout(DEFAULT_SESSION_TIMEOUT_MS);
    }

    /** Generates a URL-safe base64 string from {@code byteLen} cryptographically random bytes. */
    public static String randomUrlSafe(int byteLen) {
        byte[] buf = new byte[byteLen];
        SECURE_RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /** Computes the PKCE S256 code-challenge for the given verifier. */
    public static String s256Challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                                       .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Reads the value previously stored at {@code key}, removes it from the session, and
     * returns it only when it equals {@code received}. Returns {@code null} on any
     * mismatch (missing, blank, or different) — caller redirects to an error page.
     */
    public static String validateAndConsumeState(Session session, String key, String received) {
        if (session == null || received == null || received.isBlank()) {
            return null;
        }
        String expected = session.get(key);
        session.remove(key);
        if (expected == null || !expected.equals(received)) {
            return null;
        }
        return expected;
    }
}
