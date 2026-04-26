package org.kinotic.core.api.config;

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
     * The allowed headers for CORS
     */
    private Set<String> allowedHeaders = Set.of("Accept", "Authorization", "Content-Type");

    /**
     * If set will set the CORS Access-Control-Allow-Credentials header to this value
     * If true then allowed origins must not contain a wildcard "*"
     */
    private Boolean allowCredentials = null;
}
