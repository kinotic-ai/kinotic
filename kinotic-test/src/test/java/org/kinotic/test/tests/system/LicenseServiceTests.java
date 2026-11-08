package org.kinotic.test.tests.system;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.internal.api.ee.license.EnterpriseFeature;
import org.kinotic.domain.internal.api.ee.license.LicenseStatus;
import org.kinotic.system.api.model.LicenseDetails;
import org.kinotic.system.api.services.LicenseService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies installing a license key while the server runs: a key Minds Ignited signed becomes the license the
 * cluster runs on, and a key it did not sign is refused, leaving the installed license in place.
 */
@SpringBootTest
public class LicenseServiceTests extends KinoticTestBase {

    @Autowired
    private LicenseService licenseService;

    @Value("${kinotic.ee.licenseKey}")
    private String evaluationKey;

    @Test
    public void aSignedKeyBecomesTheCurrentLicense() throws Exception {
        LicenseDetails installed = await(licenseService.install(evaluationKey));
        LicenseDetails current = await(licenseService.findCurrent());
        assertEquals(installed.getLicensee(), current.getLicensee());
        assertEquals(LicenseStatus.ACTIVE, current.getStatus());
        assertEquals(EnumSet.allOf(EnterpriseFeature.class), current.getFeatures());
    }

    @Test
    public void aKeyMindsIgnitedDidNotSignIsRefused() throws Exception {
        LicenseDetails before = await(licenseService.findCurrent());
        assertThrows(JwtException.class, () -> licenseService.install("not-a-license-key"));
        assertEquals(before.getLicensee(), await(licenseService.findCurrent()).getLicensee());
    }
}
