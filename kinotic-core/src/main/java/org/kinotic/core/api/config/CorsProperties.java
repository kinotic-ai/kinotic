package org.kinotic.core.api.config;

import io.vertx.core.http.HttpMethod;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Set;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class CorsProperties {

    /**
     * The allowed origin pattern for CORS
     * Defaults to "http://localhost.*"
     * If you want to allow all origins use "*"
     * Internally uses Java Regex Patterns to match
     * @see java.util.regex.Pattern
     */
    private String allowedOriginPattern = "http://localhost.*";

    /**
     * The HTTP methods allowed by CORS preflight responses.
     * Vert.x's {@code CorsHandler} allows no methods by default, so this must be set
     * for any cross-origin request other than a simple GET to succeed.
     */
    private Set<HttpMethod> allowedMethods = Set.of(HttpMethod.GET,
                                                    HttpMethod.POST,
                                                    HttpMethod.PUT,
                                                    HttpMethod.PATCH,
                                                    HttpMethod.DELETE,
                                                    HttpMethod.OPTIONS);

    /**
     * The allowed headers for CORS
     */
    private Set<String> allowedHeaders = Set.of("Accept", "Authorization", "Content-Type");

    /**
     * If set will set the CORS Access-Control-Allow-Credentials header to this value
     * If true then allowed origins must not contain a wildcard "*"
     */
    private Boolean allowCredentials = null;
}
