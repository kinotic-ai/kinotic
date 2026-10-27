package org.kinotic.domain.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The Cloudflare DNS zone of the published sites domain and the token that edits it, bound under
 * {@code kinotic.domain.publishedSites.cloudflare.*}. With {@link #enabled} the platform creates each
 * organization's proxied wildcard record {@code *.<org>} in the zone when the organization is created,
 * which is what the zone's Total TLS issues the organization's certificate for and the sites Worker
 * answers; without it the record is logged instead, for an environment with no zone.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class CloudflareDnsProperties {

    /**
     * Whether organizations' records are created in Cloudflare.
     */
    private boolean enabled = false;

    /**
     * An API token holding DNS edit on the zone and nothing else. Imported from the system's secret
     * storage as a system secret, never written in a configuration file.
     */
    private String apiToken;

    /**
     * The id of the zone the sites domain is in.
     */
    private String zoneId;
}
