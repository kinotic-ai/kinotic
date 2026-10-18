package org.kinotic.domain.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The Cloudflare zone an organization's sites subdomain is provisioned in, bound under
 * {@code kinotic.domain.siteDomain.cloudflare.*}. With {@link #enabled} the platform creates the proxied
 * wildcard record {@code *.<org>} under the sites domain in the zone when an organization is created,
 * which is what the zone's Total TLS issues the organization's certificate for and the sites Worker
 * answers; without it the record is logged instead, for an environment with no edge.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class CloudflareSiteDomainProperties {

    /**
     * Whether organizations' records are created in Cloudflare.
     */
    private boolean enabled = false;

    /**
     * An API token holding DNS edit on the zone and nothing else. Imported from the platform's secret
     * storage as a platform secret, never written in a configuration file.
     */
    private String apiToken;

    /**
     * The id of the zone the sites domain is in.
     */
    private String zoneId;
}
