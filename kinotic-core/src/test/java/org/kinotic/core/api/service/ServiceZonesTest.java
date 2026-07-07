package org.kinotic.core.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the zone grammar and the app zone construction rules.
 */
public class ServiceZonesTest {

    @Test
    public void validZones() {
        assertDoesNotThrow(() -> ServiceZones.validateZone("api"));
        assertDoesNotThrow(() -> ServiceZones.validateZone("system"));
        assertDoesNotThrow(() -> ServiceZones.validateZone("billing"));
        assertDoesNotThrow(() -> ServiceZones.validateZone("api.admin"));
        assertDoesNotThrow(() -> ServiceZones.validateZone("app.acme-org.orders-app"));
        assertDoesNotThrow(() -> ServiceZones.validateZone("zone2.with-dash.x9"));
    }

    @Test
    public void invalidZones() {
        // commons-lang3 Validate.notEmpty semantics: NPE for null, IAE for empty
        assertThrows(NullPointerException.class, () -> ServiceZones.validateZone(null));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.validateZone(""));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.validateZone("Api"));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.validateZone("api.*"));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.validateZone("api..admin"));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.validateZone(".api"));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.validateZone("api."));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.validateZone("-api"));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.validateZone("api-"));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.validateZone("a pi"));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.validateZone("api/admin"));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.validateZone("scope@api"));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.validateZone("api#1"));
    }

    @Test
    public void appZoneRequiresSingleLabelIds() {
        assertEquals("app.acme-org.orders-app", ServiceZones.appZone("acme-org", "orders-app"));

        // a dotted id would shift the label structure of app.<organizationId>.<applicationId>
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.appZone("acme.org", "orders-app"));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.appZone("acme-org", "orders.app"));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.appZone("acme-org", "orders.*"));
        assertThrows(IllegalArgumentException.class, () -> ServiceZones.appZone("Acme-Org", "orders-app"));
    }

}
