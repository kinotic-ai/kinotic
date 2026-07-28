package org.kinotic.core.api.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The surface a Kinotic-minted JWT may be presented to, carried as the token's {@code aud} claim.
 * Every authenticating entry point declares the one audience it accepts, so a token minted for
 * one surface is rejected everywhere else: an MCP host's token cannot open a STOMP connection,
 * and the CLI's token cannot call MCP tools. The audience also keeps a token minted by an IdP
 * (or any other party) from being replayed against the gateway.
 */
@Getter
@RequiredArgsConstructor
public enum KinoticAudience {

    /**
     * Published services reached over the STOMP gateway. Issued to the CLI by the RFC 8628
     * device-code grant.
     */
    PUBLISHED_SERVICES("kinotic"),

    /**
     * MCP tools at {@code POST /mcp}. Issued to MCP hosts by the PKCE authorization-code grant.
     */
    MCP_TOOLS("kinotic-mcp");

    /** The {@code aud} claim value tokens for this surface carry. */
    private final String claim;
}
