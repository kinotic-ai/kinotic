package org.kinotic.domain.api.model.iam;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The surface a Kinotic-minted JWT was issued for, carried as the token's {@code aud} claim.
 * Each OAuth grant stamps the audience of the surface it serves, so a token records where it was
 * meant to be used and a refresh lineage re-mints the same one.
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
