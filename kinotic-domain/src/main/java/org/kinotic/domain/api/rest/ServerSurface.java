package org.kinotic.domain.api.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;

/**
 * The URLs this server presents to one request: where a browser reaches its API, which OAuth issuer it
 * is, and where the UI its browser flows send the user lives. A server module that derives them from the
 * request supplies its own bean; any other server presents the URLs configured under {@code kinotic.domain}.
 */
public interface ServerSurface {

    /** Base URL of this server's API as a browser reaches it, the base of OIDC {@code redirect_uri}s. */
    String apiBaseUrl(RoutingContext ctx);

    /**
     * Base URL of this server's OAuth 2.1 surface: its issuer identifier, and the base of every endpoint its
     * RFC 8414 and RFC 9728 metadata advertise.
     */
    String issuerBaseUrl(RoutingContext ctx);

    /**
     * Absolute URL of {@code path} on the UI this server's browser flows send the user to. Fails when the
     * request has no such UI.
     */
    Future<String> uiUrl(RoutingContext ctx, String path);
}
