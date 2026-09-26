package org.kinotic.core.internal.utils;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.utils.ZoneUtil;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the zone grammar, how a zone is read from an address, and how zones cover their sub-zones.
 */
public class ZoneUtilTest {

    @Test
    public void validZones() {
        assertDoesNotThrow(() -> ZoneUtil.validateZone("os-api"));
        assertDoesNotThrow(() -> ZoneUtil.validateZone("app-api"));
        assertDoesNotThrow(() -> ZoneUtil.validateZone("system"));
        assertDoesNotThrow(() -> ZoneUtil.validateZone("billing"));
        assertDoesNotThrow(() -> ZoneUtil.validateZone("api.admin"));
        assertDoesNotThrow(() -> ZoneUtil.validateZone("app.acme-corp.orders-app"));
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
        // underscores are excluded so a zone is always a valid URI host
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone("os_api"));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone("_api"));
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateZone("api_"));
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
        // slugified ids no longer use underscores, so an underscore label is rejected
        assertThrows(IllegalArgumentException.class, () -> ZoneUtil.validateLabel("acme_corp"));
    }

    @Test
    public void zoneOfReadsTheZoneOfAnAddress() {
        assertEquals("management-api", ZoneUtil.zoneOf("srv://management-api~org.kinotic.Service"));
        assertEquals("app.acme.orders", ZoneUtil.zoneOf("srv://app.acme.orders~com.acme.Orders/place#1.0.0"));
        // the scope precedes the zone and never contributes to it
        assertEquals("management-api", ZoneUtil.zoneOf("srv://9a3b@management-api~org.kinotic.JobMonitoringService"));
        // a '~' in the path is not a zone delimiter
        assertNull(ZoneUtil.zoneOf("srv://org.kinotic.Service/a~b"));
        assertNull(ZoneUtil.zoneOf("srv://scope@org.kinotic.Service"));
        // vertx reply addresses carry no scheme, and so no zone
        assertNull(ZoneUtil.zoneOf("__vertx.reply.4f0c"));
    }

    @Test
    public void zoneMatchesItselfAndItsSubZones() {
        Set<String> zones = Set.of("app", "management-api");
        assertTrue(ZoneUtil.zoneMatches("app", zones));
        assertTrue(ZoneUtil.zoneMatches("app.acme.orders", zones));
        assertTrue(ZoneUtil.zoneMatches("management-api", zones));
        // the dot boundary keeps a zone that only shares a prefix out
        assertFalse(ZoneUtil.zoneMatches("app-api", zones));
        assertFalse(ZoneUtil.zoneMatches("management-api-2", zones));
        assertFalse(ZoneUtil.zoneMatches("app.acme.orders", Set.of("app.acme.order")));
    }

}
