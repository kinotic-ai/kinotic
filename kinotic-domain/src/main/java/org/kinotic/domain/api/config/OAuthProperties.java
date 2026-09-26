package org.kinotic.domain.api.config;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.Set;

/**
 * Settings for the OAuth 2.1 authorization server that MCP hosts drive to reach {@code /mcp}.
 * Those hosts identify themselves with a Client ID Metadata Document URL
 * (draft-ietf-oauth-client-id-metadata-document).
 */
@Getter
@Setter
public class OAuthProperties {

    /**
     * Client ID Metadata Document URLs permitted to start an authorization-code flow, matched
     * exactly. A client_id that is not listed is rejected, so clients are onboarded explicitly —
     * the deployment pattern of draft-ietf-oauth-client-id-metadata-document Section 6.10.
     * Required: a deployment names the clients it accepts, or fails to start.
     * <p>
     * The consent page names the client_id host, and that host is one the client had to control to
     * serve its document.
     */
    @NotEmpty
    private Set<String> allowedClientIds = Set.of();

    /**
     * Base URL this OAuth 2.1 surface is published under: the RFC 8414 issuer identifier, every
     * endpoint its metadata advertises, and the RFC 9728 resource metadata for {@code /mcp}. An MCP
     * host exchanges its authorization code from its own backend rather than from the browser, so
     * this must be reachable from the internet — which is a different host than the browser uses
     * whenever the gateway is only publicly reachable through a tunnel or a separate ingress.
     * <p>
     * When left null the platform falls back to {@code apiBaseUrl}, which is correct wherever the
     * browser and the internet reach the gateway at the same URL.
     */
    private String issuerBaseUrl = null;


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
