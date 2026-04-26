package org.kinotic.gateway.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Set;

/**
 * CORS settings applied to the api-gateway router (port 58503). Required because the
 * SPA is served from a different origin than the API in production deployments
 * (e.g. SPA on Azure Storage, API on the kinotic-server cluster).
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class CorsProperties {

    /**
     * Java regex matched against the {@code Origin} header. Use {@code "*"} to allow any
     * origin (only when {@link #allowCredentials} is null/false — credentials cannot be
     * combined with a wildcard origin).
     *
     * @see java.util.regex.Pattern
     */
    private String allowedOriginPattern = "http://localhost.*";

    /**
     * Headers permitted on cross-origin requests.
     */
    private Set<String> allowedHeaders = Set.of("Accept", "Authorization", "Content-Type");

    /**
     * Sets the {@code Access-Control-Allow-Credentials} response header when non-null.
     * Must remain null/false if {@link #allowedOriginPattern} contains a wildcard.
     */
    private Boolean allowCredentials = null;
}
