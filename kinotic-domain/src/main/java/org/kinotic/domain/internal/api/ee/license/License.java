package org.kinotic.domain.internal.api.ee.license;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * A verified Kinotic Enterprise License: who it is issued to, when it was issued and expires, and the Enterprise
 * Features it turns on. A license keeps its features on for {@link #GRACE_PERIOD} after it expires.
 *
 * @param licensee  the party the license is issued to
 * @param issuedAt  when the license was issued, or {@code null} when its key does not say
 * @param expiresAt when the license expires
 * @param features  the Enterprise Features the license turns on
 */
public record License(String licensee, Instant issuedAt, Instant expiresAt, Set<EnterpriseFeature> features) {

    /** How long after {@link #expiresAt()} a license keeps its features on. */
    public static final Duration GRACE_PERIOD = Duration.ofDays(14);

    /** Where the license stands at {@code now}. */
    public LicenseStatus status(Instant now) {
        LicenseStatus ret;
        if (now.isBefore(expiresAt)) {
            ret = LicenseStatus.ACTIVE;
        } else if (now.isBefore(graceEndsAt())) {
            ret = LicenseStatus.GRACE_PERIOD;
        } else {
            ret = LicenseStatus.EXPIRED;
        }
        return ret;
    }

    /** When the license stops turning its features on: {@link #GRACE_PERIOD} after it expires. */
    public Instant graceEndsAt() {
        return expiresAt.plus(GRACE_PERIOD);
    }

    /** Whether this license turns {@code feature} on at {@code now}. */
    public boolean allows(EnterpriseFeature feature, Instant now) {
        return features.contains(feature) && status(now) != LicenseStatus.EXPIRED;
    }

}
