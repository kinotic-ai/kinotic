package org.kinotic.gateway.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration for the static-file web server that serves the kinotic-frontend SPA.
 * <p>
 * In dev (docker-compose, local) this is enabled and serves {@code resources/webroot}.
 * In KinD / Azure deployments the SPA is hosted separately (e.g. Azure Storage Account)
 * so this verticle is disabled and only the api-gateway port (58503) is exposed.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class WebServerProperties {

    /**
     * Listening port for the static-file server. Only used when {@link #enabled} is true.
     */
    private int port = 9090;

    /**
     * When false the static-file verticle is not deployed at all. Set false in
     * environments where the SPA is hosted outside the cluster.
     */
    private boolean enabled = true;
}
