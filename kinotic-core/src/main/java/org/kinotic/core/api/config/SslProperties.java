package org.kinotic.core.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * SSL/TLS configuration for all Vert.x HTTP servers.
 * When enabled, servers use PEM-formatted certificate and key files.
 * <p>
 * Configured via Spring properties:
 * <pre>
 * kinotic.ssl.enabled=true
 * kinotic.ssl.cert-path=/certs/tls.crt
 * kinotic.ssl.key-path=/certs/tls.key
 * </pre>
 *
 * Created by Claude on 4/4/26.
 */
@Getter
@Setter
@Accessors(chain = true)
public class SslProperties {

    /**
     * Enable TLS on all Vert.x HTTP servers (STOMP, OpenAPI, GraphQL, Web).
     */
    private boolean enabled = false;

    /**
     * Path to the PEM-encoded certificate file (e.g. tls.crt from a Kubernetes TLS secret).
     */
    private String certPath;

    /**
     * Path to the PEM-encoded private key file (e.g. tls.key from a Kubernetes TLS secret).
     */
    private String keyPath;
}
