package org.kinotic.domain.api.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies application zone construction.
 */
public class PlatformZonesTest {

    @Test
    public void appZoneRequiresSingleLabelIds() {
        assertEquals("app.acme-org.orders-app", PlatformZones.appZone("acme-org", "orders-app"));

        // a dotted id would shift the label structure of app.<organizationId>.<applicationId>
        assertThrows(IllegalArgumentException.class, () -> PlatformZones.appZone("acme.org", "orders-app"));
        assertThrows(IllegalArgumentException.class, () -> PlatformZones.appZone("acme-org", "orders.app"));
        assertThrows(IllegalArgumentException.class, () -> PlatformZones.appZone("acme-org", "orders.*"));
        assertThrows(IllegalArgumentException.class, () -> PlatformZones.appZone("Acme-Org", "orders-app"));
    }

}
