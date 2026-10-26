package org.kinotic.core.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Set;
import java.util.regex.Pattern;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class CorsProperties {

    /**
     * The allowed origin pattern for CORS
     * Defaults to "http://localhost(:\\d+)?", any port on localhost
     * If you want to allow all origins use "*"
     * Internally uses Java Regex Patterns to match
     * <p>
     * Security: the origins this pattern names, other than through "*", are also the only pages the server's sign-in,
     * sign-up, invitation and GitHub App install flows return to, with their logins, tokens, codes and errors (see
     * {@link #trustsOrigin(String)}), so name only origins you control. The local development profile's ngrok wildcard is the one exception: it admits anyone's ngrok tunnel,
     * which is acceptable only on a developer's machine.
     * @see java.util.regex.Pattern
     */
    private String allowedOriginPattern = "http://localhost(:\\d+)?";

    /**
     * The allowed headers for CORS
     */
    private Set<String> allowedHeaders = Set.of("Accept", "Authorization", "Content-Type");

    /**
     * The response headers exposed to browser callers via Access-Control-Expose-Headers.
     * WWW-Authenticate is exposed by default so browser-based MCP hosts can read the OAuth
     * discovery challenge off a 401.
     */
    private Set<String> exposedHeaders = Set.of("WWW-Authenticate");

    /**
     * If set will set the CORS Access-Control-Allow-Credentials header to this value
     * If true then allowed origins must not contain a wildcard "*"
     */
    private Boolean allowCredentials = null;

    /**
     * Whether a browser flow may return to the origin with its outcome (a login, a social sign-up token, a GitHub App
     * install code, an error), which is whether {@link #getAllowedOriginPattern()} names it. A pattern of "*", which opens CORS to every site, trusts no origin.
     *
     * @param origin the origin, scheme and authority with no path, such as {@code https://portal.kinotic.ai}
     * @return true when the pattern is not "*" and the origin fully matches it
     */
    public boolean trustsOrigin(String origin) {
        return origin != null && !"*".equals(allowedOriginPattern) && Pattern.matches(allowedOriginPattern, origin);
    }

    /**
     * The regex an allowed origin fully matches: {@link #getAllowedOriginPattern()}, with {@code *} standing for any
     * origin.
     */
    public String originRegex() {
        return "*".equals(allowedOriginPattern) ? ".*" : allowedOriginPattern;
    }
}
