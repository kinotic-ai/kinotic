package org.kinotic.managementserver.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The Cloudflare Turnstile site that stands between anyone on the internet and the routes a person must be at the
 * browser for, bound under {@code kinotic.managementServer.turnstile.*}: the portal presents the site's challenge
 * and sends the token it earns, which the management server verifies with the site's secret.
 */
@Getter
@Setter
@Accessors(chain = true)
public class TurnstileProperties {

    /**
     * Whether the challenge is required. An environment with no Turnstile site, such as the e2e stack, turns it off
     * and accepts every request.
     */
    private boolean enabled = true;

    /** The site key, which the portal renders the challenge with; public. */
    private String siteKey;

    /**
     * The site's secret key, which every token is verified with. Imported from the system's secret storage as a
     * system secret, never written in a configuration file.
     */
    private String secretKey;
}
