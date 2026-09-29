package org.kinotic.core.api.event;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies which addresses a partitioning hosts and reaches, and the node attributes it advertises.
 */
public class ZonePartitioningServiceTest {

    private static final ZonePartitioningService ORG = ZonePartitioningService.of("org",
                                                                           Set.of("management-api"),
                                                                           Set.of("management-api", "system-api", "app-api"));

    @Test
    public void hostsOnlyItsZones() {
        assertTrue(ORG.hosts("srv://management-api~org.kinotic.management.api.services.ProjectService"));
        assertFalse(ORG.hosts("srv://system-api~org.kinotic.system.api.services.workload.VmNodeOrchestrationService"));
        assertFalse(ORG.hosts("srv://app.acme.orders~com.acme.Orders"));
    }

    @Test
    public void reachesItsReachableZones() {
        assertTrue(ORG.reaches("srv://system-api~org.kinotic.system.api.services.workload.VmNodeOrchestrationService"));
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
        ZonePartitioningService app = ZonePartitioningService.of("app", Set.of("app-api", "app"), Set.of("app-api", "app"));
        assertTrue(app.hosts("srv://app.acme.orders.billing~com.acme.Billing"));
        assertFalse(app.hosts("srv://management-api~org.kinotic.management.api.services.ProjectService"));
    }

    @Test
    public void everyZoneHostsAndReachesAnything() {
        ZonePartitioningService every = ZonePartitioningService.everyZone("kinotic");
        assertTrue(every.hosts("srv://evil~com.example.Service"));
        assertTrue(every.reaches("srv://app.acme.orders~com.acme.Orders"));
        assertEquals(Map.of(), every.nodeAttributes());
    }

    @Test
    public void advertisesAnAttributePerHostedZone() {
        ZonePartitioningService system = ZonePartitioningService.of("system",
                                                             Set.of("system-api", "management-api"),
                                                             Set.of("system-api", "management-api"));
        assertEquals(Map.of("kinotic.zone.system-api", true, "kinotic.zone.management-api", true),
                     system.nodeAttributes());
    }

    @Test
    public void mustReachWhatItHosts() {
        assertThrows(IllegalArgumentException.class,
                     () -> ZonePartitioningService.of("broken", Set.of("app-api"), Set.of("management-api")));
    }
}
