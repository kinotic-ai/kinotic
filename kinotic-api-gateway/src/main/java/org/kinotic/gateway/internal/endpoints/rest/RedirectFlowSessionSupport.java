package org.kinotic.gateway.internal.endpoints.rest;

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



    private RedirectFlowSessionSupport() {}





}
