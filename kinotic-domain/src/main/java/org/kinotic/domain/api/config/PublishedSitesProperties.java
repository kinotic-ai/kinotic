package org.kinotic.domain.api.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.Validate;

/**
 * Where organizations' published sites are hosted, bound under {@code kinotic.domain.publishedSites.*}: the
 * domain every site is under, {@code <app>--<ui>.<org>.<domain>}, the scheme and port a browser reaches the sites
 * on, and the Cloudflare DNS zone of that domain, in which the system creates the record each organization's
 * sites resolve by.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PublishedSitesProperties {

    /**
     * The domain every organization's sites are under, e.g. {@code kinotic.io}: the UI {@code admin} of application
     * {@code shop} of organization {@code acme} is served at {@code shop--admin.acme.kinotic.io}. A subdomain of the
     * edge's zone names the environment, e.g. {@code dev-nm.kinotic.io}.
     */
    private String domain;

    /**
     * Whether a browser reaches the sites over TLS.
     */
    private boolean useSsl = true;

    /**
     * The port a browser reaches the sites on, or {@code null} for the default port of the scheme.
     */
    private Integer port;

    /**
     * The Cloudflare zone the domain is in, where each organization's record is created.
     */
    @Valid
    private CloudflareDnsProperties cloudflare = new CloudflareDnsProperties();

    /**
     * The hostname a site with the given id is served at.
     *
     * @throws IllegalArgumentException when {@link #domain} is not set
     */
    public String resolveHostname(String siteId) {
        Validate.notBlank(domain, "kinotic.domain.publishedSites.domain is required");
        return siteId + "." + domain;
    }

    /**
     * Where a site with the given id is served.
     *
     * @throws IllegalArgumentException when {@link #domain} is not set
     */
    public String resolveSiteUrl(String siteId) {
        String portSuffix = port == null ? "" : ":" + port;
        return (useSsl ? "https://" : "http://") + resolveHostname(siteId) + portSuffix;
    }
}
