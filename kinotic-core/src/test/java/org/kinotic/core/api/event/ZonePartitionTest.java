package org.kinotic.core.api.event;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies which addresses a partition hosts and reaches, and the node attributes it advertises.
 */
public class ZonePartitionTest {

    private static final ZonePartition ORG = ZonePartition.of("org",
                                                              Set.of("management-api"),
                                                              Set.of("management-api", "system-api", "app-api"));

    @Test
    public void hostsOnlyItsZones() {
        assertTrue(ORG.hosts("srv://management-api~org.kinotic.management.api.services.ProjectService"));
        assertFalse(ORG.hosts("srv://system-api~org.kinotic.system.api.services.LogManager"));
        assertFalse(ORG.hosts("srv://app.acme.orders~com.acme.Orders"));
    }

    @Test
    public void reachesItsReachableZones() {
        assertTrue(ORG.reaches("srv://system-api~org.kinotic.system.api.services.LogManager"));
        assertTrue(ORG.reaches("srv://app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository"));
        assertFalse(ORG.reaches("srv://app.acme.orders~com.acme.Orders"));
    }

    @Test
    public void passesAddressesWithoutAZone() {
        assertTrue(ORG.hosts("srv://org.kinotic.core.api.Service"));
        assertTrue(ORG.reaches("__vertx.reply.4f0c"));
        assertTrue(ORG.reaches("topic://org.kinotic.domain.api.model.WatchEvent"));
    }

    @Test
    public void coversSubZones() {
        ZonePartition app = ZonePartition.of("app", Set.of("app-api", "app"), Set.of("app-api", "app"));
        assertTrue(app.hosts("srv://app.acme.orders.billing~com.acme.Billing"));
        assertFalse(app.hosts("srv://management-api~org.kinotic.management.api.services.ProjectService"));
    }

    @Test
    public void everyZoneHostsAndReachesAnything() {
        ZonePartition every = ZonePartition.everyZone("kinotic");
        assertTrue(every.hosts("srv://evil~com.example.Service"));
        assertTrue(every.reaches("srv://app.acme.orders~com.acme.Orders"));
        assertEquals(Map.of(), every.nodeAttributes());
    }

    @Test
    public void advertisesAnAttributePerHostedZone() {
        ZonePartition system = ZonePartition.of("system",
                                                Set.of("system-api", "management-api"),
                                                Set.of("system-api", "management-api"));
        assertEquals(Map.of("kinotic.zone.system-api", true, "kinotic.zone.management-api", true),
                     system.nodeAttributes());
    }

    @Test
    public void mustReachWhatItHosts() {
        assertThrows(IllegalArgumentException.class,
                     () -> ZonePartition.of("broken", Set.of("app-api"), Set.of("management-api")));
    }
}
