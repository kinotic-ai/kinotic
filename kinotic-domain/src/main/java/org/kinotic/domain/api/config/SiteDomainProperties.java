package org.kinotic.domain.api.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The domain every organization's published sites are served under, and the edge that serves them, bound
 * under {@code kinotic.domain.siteDomain.*}. Each organization's sites live under its own subdomain of
 * {@link #domain}, {@code <app>--<ui>.<org>.<domain>}, which the edge needs a DNS record and a certificate
 * for; {@link #cloudflare} is the edge that provides both from one record the platform creates when the
 * organization is created.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class SiteDomainProperties {

    /**
     * The domain every organization's sites are under, e.g. {@code kinotic.io}: the same value the system
     * server serves sites under as {@code kinotic.systemApi.uiDeployment.sitesDomain}. A subdomain of the
     * edge's zone names the environment, e.g. {@code dev-nm.kinotic.io}.
     */
    private String domain;

    /**
     * The Cloudflare zone the domain is in, where each organization's record is created.
     */
    @Valid
    private CloudflareSiteDomainProperties cloudflare = new CloudflareSiteDomainProperties();
}
