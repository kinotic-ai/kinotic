package org.kinotic.core.internal.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the zone grammar.
 */
public class ZoneUtilTest {

    @Test
    public void validZones() {
        assertDoesNotThrow(() -> ZoneUtil.validateZone("api"));
        assertDoesNotThrow(() -> ZoneUtil.validateZone("system"));
        assertDoesNotThrow(() -> ZoneUtil.validateZone("billing"));
        assertDoesNotThrow(() -> ZoneUtil.validateZone("api.admin"));
        assertDoesNotThrow(() -> ZoneUtil.validateZone("app.acme-org.orders-app"));
        assertDoesNotThrow(() -> ZoneUtil.validateZone("zone2.with-dash.x9"));
    }

    @Test
    public void invalidZones() {
        // commons-lang3 Validate.notEmpty semantics: NPE for null, IAE for empty
        assertThrows(NullPointerException.class, () -> ZoneUtil.validateZone(null));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone(""));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone("Api"));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone("api.*"));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone("api..admin"));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone(".api"));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone("api."));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone("-api"));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone("api-"));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone("a pi"));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone("api/admin"));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone("scope@api"));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone("api#1"));
    }

    @Test
    public void labelsMustBeSingleAndDotFree() {
        assertDoesNotThrow(() -> ZoneUtil.validateLabel("acme-org"));
        assertDoesNotThrow(() -> ZoneUtil.validateLabel("orders-app"));

        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateLabel("acme.org"));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateLabel("orders.*"));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateLabel("Acme-Org"));
    }

}
