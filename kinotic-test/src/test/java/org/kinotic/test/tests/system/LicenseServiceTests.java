package org.kinotic.test.tests.system;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.internal.api.ee.license.EnterpriseFeature;
import org.kinotic.domain.internal.api.ee.license.LicenseStatus;
import org.kinotic.system.api.model.LicenseDetails;
import org.kinotic.system.api.services.LicenseService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.EnumSet;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies installing a license key while the server runs: a key Minds Ignited signed becomes the license the
 * cluster runs on, and a key it did not sign is refused, leaving the installed license in place.
 */
@SpringBootTest
public class LicenseServiceTests extends KinoticTestBase {

    // the evaluation key, which V7__evaluation_license installs for the test environment
    private static final String EVALUATION_KEY = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJLaW5vdGljIEV2YWx1YXRpb24gLSBkZXZlbG9wbWVudCBhbmQgdGVzdGluZyBvbmx5LCBub3QgZm9yIHByb2R1Y3Rpb24gdXNlIiwiaWF0IjoxNzkzODQxOTgxLCJleHAiOjIxMTQyOTQ0MDAsImZlYXR1cmVzIjpbIioiXX0.41zerfYDi74rzxfDfRqUE_WFK44lvtOt2MKAY9_ECmsCpDaWJO2MuLvU_NGfXgV69XxVS3pkxMSB0JGIa1hKRg";

    @Autowired
    private LicenseService licenseService;

    @Test
    public void aSignedKeyBecomesTheCurrentLicense() throws Exception {
        LicenseDetails installed = await(licenseService.install(EVALUATION_KEY));
        LicenseDetails current = await(licenseService.findCurrent());
        assertEquals(installed.getLicensee(), current.getLicensee());
        assertEquals(LicenseStatus.ACTIVE, current.getStatus());
        assertEquals(EnumSet.allOf(EnterpriseFeature.class), current.getFeatures());
    }

    @Test
    public void aKeyMindsIgnitedDidNotSignIsRefused() throws Exception {
        LicenseDetails before = await(licenseService.install(EVALUATION_KEY));
        ExecutionException refused = assertThrows(ExecutionException.class, () -> await(licenseService.install("not-a-license-key")));
        assertInstanceOf(JwtException.class, refused.getCause());
        assertEquals(before.getLicensee(), await(licenseService.findCurrent()).getLicensee());
    }
}
