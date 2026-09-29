package org.kinotic.domain.api.rest;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * An RFC 6749 §4.5 extension grant the OAuth token endpoint redeems. The OAuth authorization server collects
 * every {@link OAuthExtensionGrant} bean, so a module adds a grant simply by publishing an implementation: the
 * token endpoint dispatches the requests carrying its {@link #grantType()} to it, and the RFC 8414 metadata
 * lists that grant type along with whatever {@link #advertise(JsonObject, String)} adds.
 */
public interface OAuthExtensionGrant {

    /**
     * The absolute URI a client requests this grant with as {@code grant_type}.
     */
    String grantType();

    /**
     * Adds this grant's own entries, such as the endpoint that starts it, to the RFC 8414 metadata of
     * {@code issuer}.
     *
     * @param metadata the authorization server metadata being answered
     * @param issuer   the issuer the metadata describes
     */
    void advertise(JsonObject metadata, String issuer);

    /**
     * Answers a token endpoint request carrying this grant's {@link #grantType()}.
     *
     * @param ctx the token request
     */
    void redeem(RoutingContext ctx);
}
