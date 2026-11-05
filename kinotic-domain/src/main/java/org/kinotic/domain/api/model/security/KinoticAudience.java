package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.AuthenticationContext;

/**
 * The surface a Kinotic-minted JWT was issued for, carried as the token's {@code aud} claim and named by the
 * entry point serving the surface as what it authenticates for. Each OAuth grant stamps the audience of the
 * surface it serves, so a token records where it was meant to be used and a refresh lineage re-mints the same one.
 */
@Getter
@RequiredArgsConstructor
public enum KinoticAudience {

    /**
     * Published services reached over the STOMP gateway. Issued to the CLI by the RFC 8628
     * device-code grant.
     */
    PUBLISHED_SERVICES(AuthenticationContext.PUBLISHED_SERVICES),

    /**
     * MCP tools at {@code POST /mcp}. Issued to MCP hosts by the PKCE authorization-code grant.
     */
    MCP_TOOLS("kinotic-mcp");

    /** The {@code aud} claim value tokens for this surface carry. */
    private final String claim;
}
