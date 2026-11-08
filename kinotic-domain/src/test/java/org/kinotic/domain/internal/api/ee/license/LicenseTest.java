package org.kinotic.domain.internal.api.ee.license;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link License}: its features stay on through the grace period after it expires and go off after it.
 */
class LicenseTest {

    private static final Instant EXPIRES_AT = Instant.parse("2027-01-01T00:00:00Z");

    private final License license = new License("Acme", Instant.parse("2026-12-01T00:00:00Z"), EXPIRES_AT,
                                                EnumSet.of(EnterpriseFeature.AZURE_KEY_VAULT));

    @Test
    void featuresStayOnThroughTheGracePeriod() {
        Instant beforeExpiry = EXPIRES_AT.minus(1, ChronoUnit.SECONDS);
        Instant inGrace = EXPIRES_AT.plus(License.GRACE_PERIOD).minus(1, ChronoUnit.SECONDS);
        Instant graceEnded = EXPIRES_AT.plus(License.GRACE_PERIOD);
        assertEquals(LicenseStatus.ACTIVE, license.status(beforeExpiry));
        assertEquals(LicenseStatus.GRACE_PERIOD, license.status(EXPIRES_AT));
        assertEquals(LicenseStatus.GRACE_PERIOD, license.status(inGrace));
        assertEquals(LicenseStatus.EXPIRED, license.status(graceEnded));
        assertTrue(license.allows(EnterpriseFeature.AZURE_KEY_VAULT, inGrace));
        assertFalse(license.allows(EnterpriseFeature.AZURE_KEY_VAULT, graceEnded));
        assertFalse(license.allows(EnterpriseFeature.ENTERPRISE_IDENTITY_PROVIDERS, beforeExpiry));
    }

}
