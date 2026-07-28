package org.kinotic.domain.api.config;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.Set;

/**
 * Settings for the OAuth 2.1 authorization server that MCP hosts drive to reach {@code /mcp}.
 * Clients identify themselves with a Client ID Metadata Document URL
 * (draft-ietf-oauth-client-id-metadata-document).
 */
@Getter
@Setter
public class OAuthProperties {

    /**
     * Client ID Metadata Document URLs permitted to start an authorization-code flow, matched
     * exactly. Empty accepts any URL that validates, which is what lets an MCP host onboard with
     * no prior arrangement; populating it onboards named clients only, the enterprise deployment
     * pattern of draft-ietf-oauth-client-id-metadata-document Section 6.10.
     * <p>
     * The consent page names the client_id host either way, and that host is one the client had to
     * control to serve its document.
     */
    private Set<String> allowedClientIds = Set.of();

    /**
     * How long a validated client metadata document is reused before it is fetched again. Bounds
     * how long the authorization server keeps honouring metadata the client has since changed.
     */
    private Duration clientMetadataCacheTtl = Duration.ofHours(1);

    /**
     * Largest client metadata document accepted, in bytes. The default is the 5 KB recommended by
     * draft-ietf-oauth-client-id-metadata-document Section 6.6.
     */
    private int clientMetadataMaxBytes = 5120;

    /**
     * How long to wait for a client metadata document before abandoning the authorization request.
     */
    private Duration clientMetadataFetchTimeout = Duration.ofSeconds(5);
}
