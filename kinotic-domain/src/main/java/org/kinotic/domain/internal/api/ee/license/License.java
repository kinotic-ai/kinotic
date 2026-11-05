package org.kinotic.domain.internal.api.ee.license;

import java.time.Instant;
import java.util.Set;

/**
 * A verified Kinotic Enterprise License: who it is issued to, when it expires, and the Enterprise Features it turns
 * on.
 *
 * @param licensee  the party the license is issued to
 * @param expiresAt when the license stops turning its features on
 * @param features  the Enterprise Features the license turns on
 */
public record License(String licensee, Instant expiresAt, Set<EnterpriseFeature> features) {

    /** Whether this license turns {@code feature} on. */
    public boolean allows(EnterpriseFeature feature) {
        return features.contains(feature);
    }
}
